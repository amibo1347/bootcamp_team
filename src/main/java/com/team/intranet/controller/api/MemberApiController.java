package com.team.intranet.controller.api;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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
    public ResponseEntity<?> verify(@RequestParam String companyCode, HttpSession session) {
        Long companyId = memberService.getVerifyCompanyId(companyCode);
        if (companyId != null) {
            // 세션에 "verifiedCompanyId"라는 이름으로 저장
            session.setAttribute("verifiedCompanyId", companyId);
            return ResponseEntity.ok(Map.of("isVerify", true));
        }
        return ResponseEntity.ok(Map.of("isVerify", false));
    }
}
