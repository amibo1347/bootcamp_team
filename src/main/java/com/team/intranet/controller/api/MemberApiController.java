package com.team.intranet.controller.api;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team.intranet.dto.MemberDto;
import com.team.intranet.enums.member.MemberType;
import com.team.intranet.service.MemberService;
import com.team.intranet.session.MemberSession;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;

    @GetMapping("/check-id")
    public ResponseEntity<Boolean> checkId(@RequestParam("loginId") String loginId) {
        boolean isDuplicate = memberService.isDuplicateId(loginId);
        return ResponseEntity.ok(isDuplicate); // true/false 데이터를 반환
    }

    @GetMapping("/company/verify")
    public Map<String, Object> verifyCompany(@RequestParam String companyCode, HttpSession session) {
        Long companyId = memberService.getVerifyCompanyId(companyCode);
        Map<String, Object> response = new HashMap<>();

        if (companyId != null) {
            String logoPath = memberService.getLogoPath(companyId);

            session.setAttribute("verifiedCompanyId", companyId);
            session.setAttribute("verifiedCompanyCode", companyCode);
            session.setAttribute("logoPath", logoPath);
            response.put("isVerify", true);
            response.put("companyId", companyId);
        } else {
            response.put("isVerify", false);
        }
        return response;
    }

    // 프로필 사진 조회
    @GetMapping("/{id}/profileImg")
    public ResponseEntity<byte[]> getProfileImg(@PathVariable Long id) {
        try {
            byte[] profileImg = memberService.getProfileImg(id);

            // 1. DB에 이미지가 있는 경우
            if (profileImg != null && profileImg.length > 0) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                        .body(profileImg);
            }

            // 2. DB에 없으면 기본 이미지 로드
            ClassPathResource resource = new ClassPathResource("static/images/user/default_user.jpg");

            if (resource.exists()) {
                byte[] defaultImg = resource.getInputStream().readAllBytes();
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "image/jpeg") // 파일 확장자에 맞춰 수정
                        .body(defaultImg);
            } else {
                return ResponseEntity.notFound().build(); // 404를 리턴해서 서버가 죽지 않게 함
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 로그인 회원 본인 프로필 사진 수정 (POST, multipart profileImg).
     *  - subAdmin 직원 수정 모달과 동일하게 FormData 키 이름 profileImg 사용.
     */
    @PostMapping("/me/profileImg")
    public ResponseEntity<Map<String, Object>> updateMyProfileImg(
            @RequestParam("profileImg") MultipartFile profileImg,
            HttpSession session) throws IOException {

        MemberSession ms = (MemberSession) session.getAttribute("memberSession");
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (profileImg == null || profileImg.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        memberService.updateMyProfileImg(ms, profileImg.getBytes());

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        return ResponseEntity.ok(body);
    }

    /**
     * 로그인 회원 본인 정보 수정 (이름·이메일·전화·생년월일).
     *  - 조직도 구성의 /api/subAdmin/update/{id} 와 같은 파라미터 패턴 (FormData).
     *  - 부서/직급/프로필 사진은 변경 안 함 (각자 별도 경로).
     *  - 갱신 후 세션을 새 MemberSession 으로 교체해 사이드바·헤더 등에 즉시 반영.
     */
    @PostMapping("/me/update")
    public ResponseEntity<Map<String, Object>> updateMyInfo(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String birthDay,
            HttpSession session) {

        MemberSession ms = (MemberSession) session.getAttribute("memberSession");
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        MemberSession updated = memberService.updateMyInfo(ms, name, email, phone, parseBirthDay(birthDay));
        session.setAttribute("memberSession", updated);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        return ResponseEntity.ok(body);
    }

    /** "yyyy-MM-dd" 또는 "yyyyMMdd" → LocalDateTime (시간은 00:00). blank 면 null. */
    private LocalDateTime parseBirthDay(String input) {
        if (input == null || input.isBlank()) return null;
        String digits = input.replaceAll("-", "");
        return LocalDate.parse(digits, DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay();
    }

}
