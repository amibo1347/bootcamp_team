package com.team.intranet.controller.api;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.team.intranet.dto.MemberDto;
import com.team.intranet.enums.member.MemberType;
import com.team.intranet.service.MemberService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;

    @GetMapping("/check-id")
    @ResponseBody // 페이지가 아닌 '데이터'를 반환하기 위해 필수!
    public ResponseEntity<Boolean> checkId(@RequestParam("loginId") String loginId) {
        boolean isDuplicate = memberService.isDuplicateId(loginId);
        return ResponseEntity.ok(isDuplicate); // true/false 데이터를 반환
    }

    @GetMapping("/company/verify")
    @ResponseBody
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

    @PostMapping("/signup") // 회원 가입
    public String newMember(MemberDto dto) {

        MemberType result = memberService.join(dto);

        switch (result) {
            case JOIN_SUCCESS:
                // 회원가입 성공 시 로그인 페이지로 보냅니다.
                return "redirect:/member/login";

            case NOT_COMPANY:
            case ALREADY_MEMBER:
            case NOT_MATCH_PASSWORD:
            default:
                // 실패 시 다시 회원가입 폼으로 보냅니다.
                return "redirect:/member/signup?error=" + result.name();
        }
    }

    // 프로필 사진 조회
    @GetMapping("/{id}/profileImg")
    @ResponseBody
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
            e.printStackTrace(); // 어떤 에러인지 콘솔에 상세히 찍음
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


}
