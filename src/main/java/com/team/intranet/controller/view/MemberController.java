package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.team.intranet.service.MemberService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/member") // 전체 매핑에 member 설정
@RequiredArgsConstructor
public class MemberController {

    ////////////////////////////////////////
/// 의존성 주입
    private final MemberService memberService;

    ////////////////////////////////////////
/// 비즈니스 로직
/// 
    @GetMapping("/login")
public String loginForm(HttpServletRequest request, Model model) {
    HttpSession session = request.getSession();
    String errorMsg = (String) session.getAttribute("loginError");

    if (errorMsg != null) {
        model.addAttribute("errorMessage", errorMsg);
        session.removeAttribute("loginError"); 
    }

    return "member/signin";
}
    
    @GetMapping("/signup")
public String signupPage(HttpSession session, Model model) {
    Long companyId = (Long) session.getAttribute("verifiedCompanyId");
    String companyCode = (String) session.getAttribute("verifiedCompanyCode");
    String verifiedLogoPath = (String) session.getAttribute("logoPath");
    if (companyId == null) {
        return "redirect:login"; // 인증 안 했으면 다시 로그인/모달창으로
    }
    if (verifiedLogoPath != null) {
        model.addAttribute("logoPath", verifiedLogoPath);
    }
    model.addAttribute("companyId", companyId);
    model.addAttribute("companyCode", companyCode);
    return "member/signup";
}
}
