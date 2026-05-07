package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.team.intranet.dto.MemberDto;
import com.team.intranet.dto.MemberDto;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Position;

import com.team.intranet.enums.member.MemberType;
import com.team.intranet.service.MemberService;
import com.team.intranet.service.DeptService;
import com.team.intranet.service.PositionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Controller
@RequestMapping("/member") // 전체 매핑에 member 설정
@RequiredArgsConstructor
public class MemberController {

    ////////////////////////////////////////
/// 의존성 주입
    private final MemberService memberService;
    private final DeptService deptService;
    private final PositionService positionService;

    ////////////////////////////////////////
/// 비즈니스 로직
/// 
    @GetMapping("/login")
public String loginForm(HttpServletRequest request, Model model) {

    HttpSession session = request.getSession();
    Object loginError = session.getAttribute("loginError");

    if (loginError != null) {
        // 2. 모델에 담아서 화면으로 넘깁니다.
        model.addAttribute("errorMessage", loginError.toString());
        
        // 3. 사용한 메시지는 세션에서 즉시 삭제합니다. (새로고침 방지)
        session.removeAttribute("loginError");
    }

    return "member/signin";
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
    
    @GetMapping("/signup")
    public String signupPage(HttpSession session, Model model) {
    Long companyId = (Long) session.getAttribute("verifiedCompanyId");
    String companyCode = (String) session.getAttribute("verifiedCompanyCode");
    String verifiedLogoPath = (String) session.getAttribute("logoPath");
    
    if (companyId == null) {
        return "redirect:/member/login"; // 인증 안 했으면 다시 로그인/모달창으로
    }
    if (verifiedLogoPath != null) {
        model.addAttribute("logoPath", verifiedLogoPath);
    }
    List<Dept> departments = deptService.findAll(companyId);
    List<Position> positions = positionService.findAll(companyId);

    model.addAttribute("companyId", companyId);
    model.addAttribute("companyCode", companyCode);
    model.addAttribute("departments", departments);
    model.addAttribute("positions", positions);
    return "member/signup";
}

}
