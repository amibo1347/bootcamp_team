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

import com.team.intranet.config.VerifyCompanyRateLimiter;
import com.team.intranet.dto.MemberDto;
import com.team.intranet.enums.member.MemberType;
import com.team.intranet.service.MemberService;
import com.team.intranet.session.MemberSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;
    private final VerifyCompanyRateLimiter verifyRateLimiter;

    @GetMapping("/check-id")
    public ResponseEntity<Boolean> checkId(@RequestParam("loginId") String loginId,
                                           @RequestParam("companyId") Long companyId) {
        // loginId(사번/아이디)는 회사 단위로만 유니크 → 회사 안에서만 중복 검사.
        boolean isDuplicate = memberService.isDuplicateId(companyId, loginId);
        return ResponseEntity.ok(isDuplicate); // true/false 데이터를 반환
    }

    /**
     * 회원가입용 기업 코드 인증.
     *  - expectedCompanyId : 현재 접속 중인 회사 로그인 페이지(/{도메인}/login)의 회사.
     *    입력한 코드가 이 회사의 것이 아니면 인증 거부 → 다른 회사로의 가입을 차단한다.
     *  - 무차별 대입 방지: 같은 IP 에서 10분 / 5회 실패 초과 시 429.
     *    성공 시 카운터 리셋 (정상 사용자 1회면 통과 → NAT 뒤 다른 사용자에게 영향 없음).
     */
    @GetMapping("/company/verify")
    public ResponseEntity<Map<String, Object>> verifyCompany(@RequestParam String companyCode,
                                             @RequestParam("expectedCompanyId") Long expectedCompanyId,
                                             HttpSession session,
                                             HttpServletRequest request) {
        String ip = clientIp(request);

        if (!verifyRateLimiter.isAllowed(ip)) {
            Map<String, Object> blocked = new HashMap<>();
            blocked.put("isVerify", false);
            blocked.put("message", "시도 횟수가 너무 많습니다. 잠시 후 다시 시도해주세요.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(blocked);
        }

        Long companyId = memberService.getVerifyCompanyId(companyCode);
        Map<String, Object> response = new HashMap<>();

        if (companyId == null || !companyId.equals(expectedCompanyId)) {
            verifyRateLimiter.markFailure(ip);
            response.put("isVerify", false);
            response.put("message", "인증 코드가 일치하지 않습니다.");
            return ResponseEntity.ok(response);
        }

        verifyRateLimiter.markSuccess(ip);
        String logoPath = memberService.getLogoPath(companyId);
        session.setAttribute("verifiedCompanyId", companyId);
        session.setAttribute("verifiedCompanyCode", companyCode);
        session.setAttribute("logoPath", logoPath);
        response.put("isVerify", true);
        response.put("companyId", companyId);
        return ResponseEntity.ok(response);
    }

    /** 리버스 프록시 뒤 환경 대비 — X-Forwarded-For 첫 값을 우선, 없으면 remoteAddr 폴백. */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    // 프로필 사진 조회 — 회사 스코프 한정 (다른 회사 회원 사진 조회 차단).
    @GetMapping("/{id}/profileImg")
    public ResponseEntity<byte[]> getProfileImg(@PathVariable Long id, HttpSession session) {
        try {
            MemberSession ms = (MemberSession) session.getAttribute("memberSession");
            // 본인 요청이 아니면 회사 스코프 검증. 존재하지 않는 ID 면 404, 타 회사면 403.
            if (!ms.getMemberId().equals(id)) {
                Long targetCompanyId = memberService.getCompanyIdByMemberId(id);
                if (targetCompanyId == null) {
                    return ResponseEntity.notFound().build();
                }
                if (!targetCompanyId.equals(ms.getCompanyId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }

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

    /**
     * 로그인 회원 본인 비밀번호 변경 (내 프로필 페이지 → 비밀번호 변경 모달).
     *  - MASTER 의 POST /master/account/password 와 동일한 입력 패턴 (current/new/confirm).
     *  - 입력값 검증은 컨트롤러에서, 실제 비번 검증·저장은 MemberService 에서.
     *  - 성공/실패 모두 JSON 으로 응답 → 모달 안에서 alert 로 노출.
     */
    @PostMapping("/me/password")
    public ResponseEntity<Map<String, Object>> changeMyPassword(
            @RequestParam(name = "currentPassword", required = false) String currentPassword,
            @RequestParam(name = "newPassword", required = false) String newPassword,
            @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
            HttpSession session) {

        MemberSession ms = (MemberSession) session.getAttribute("memberSession");
        String error = validatePasswordInput(currentPassword, newPassword, confirmPassword);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", error));
        }

        try {
            memberService.changePassword(ms, currentPassword, newPassword);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "비밀번호가 변경되었습니다."));
    }

    /** MasterAccountController.validate 와 동일 패턴 — 문제가 없으면 null, 있으면 사용자 메시지 반환. */
    private String validatePasswordInput(String current, String next, String confirm) {
        if (isBlank(current) || isBlank(next) || isBlank(confirm)) {
            return "모든 항목을 입력하세요.";
        }
        if (next.length() < MIN_PASSWORD_LENGTH) {
            return "새 비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.";
        }
        if (!next.equals(confirm)) {
            return "새 비밀번호와 확인이 일치하지 않습니다.";
        }
        if (next.equals(current)) {
            return "새 비밀번호가 현재 비밀번호와 같습니다.";
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static final int MIN_PASSWORD_LENGTH = 8;
}
