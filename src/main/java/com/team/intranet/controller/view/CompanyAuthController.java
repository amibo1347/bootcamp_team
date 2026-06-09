package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.team.intranet.dto.MemberDto;
import com.team.intranet.entity.Company;
import com.team.intranet.enums.member.MemberType;
import com.team.intranet.service.CompanyService;
import com.team.intranet.service.DeptService;
import com.team.intranet.service.MemberService;
import com.team.intranet.service.PositionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 회사 단위 로그인·회원가입 라우트.
 *  - loginId(사번/아이디)가 회사별 스코프이므로 로그인/가입은 반드시 회사가 특정된 상태로 진행한다.
 *  - URL 의미:
 *      /company-login            → 회사 선택(도메인 입력) 랜딩
 *      /{companyDomain}/login    → 그 회사의 로그인 페이지
 *      /{companyDomain}/signup   → 그 회사의 회원가입 페이지(기업 코드 인증 통과 후)
 */
@Controller
@RequiredArgsConstructor
public class CompanyAuthController {

    private final CompanyService companyService;
    private final MemberService memberService;
    private final DeptService deptService;
    private final PositionService positionService;

    /** 회사 선택 랜딩 — 소속 회사의 도메인을 입력해 그 회사 로그인 페이지로 이동한다. */
    @GetMapping("/company-login")
    public String companyLanding(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession();
        Object loginError = session.getAttribute("loginError");
        if (loginError != null) {
            model.addAttribute("errorMessage", loginError.toString());
            session.removeAttribute("loginError");
        }
        return "member/login-landing";
    }

    /** 회사별 로그인 페이지. */
    @GetMapping("/{companyDomain}/login")
    public String companyLogin(@PathVariable String companyDomain,
                               HttpServletRequest request, Model model) {
        Company company = companyService.findByDomain(companyDomain);
        if (company == null) {
            return "redirect:/company-login"; // 존재하지 않는 회사 도메인
        }

        HttpSession session = request.getSession();
        Object loginError = session.getAttribute("loginError");
        if (loginError != null) {
            model.addAttribute("errorMessage", loginError.toString());
            session.removeAttribute("loginError");
        }

        model.addAttribute("companyId", company.getCompanyId());
        model.addAttribute("companyDomain", company.getCompanyDomain());
        model.addAttribute("companyName", company.getCompanyName());
        model.addAttribute("usesEmployeeNo", company.usesEmployeeNo());
        model.addAttribute("logoPath",
                companyService.hasLogo(company.getCompanyId())
                        ? "/api/company/" + company.getCompanyId() + "/logo" : null);
        return "member/signin";
    }

    /**
     * 회사별 회원가입 페이지.
     *  - 기업 코드 인증(/api/member/company/verify)을 통과해 세션에 verifiedCompanyId 가 있어야 한다.
     *  - 인증한 회사 ≠ 이 페이지 회사면 로그인 페이지로 돌려보낸다.
     */
    @GetMapping("/{companyDomain}/signup")
    public String signupPage(@PathVariable String companyDomain, HttpSession session, Model model) {
        Company company = companyService.findByDomain(companyDomain);
        if (company == null) {
            return "redirect:/company-login";
        }

        Long verifiedCompanyId = (Long) session.getAttribute("verifiedCompanyId");
        if (verifiedCompanyId == null || !verifiedCompanyId.equals(company.getCompanyId())) {
            // 기업 코드 인증을 안 했거나 다른 회사 코드로 인증함 → 로그인 페이지에서 다시.
            return "redirect:/" + company.getCompanyDomain() + "/login";
        }

        String verifiedLogoPath = (String) session.getAttribute("logoPath");
        if (verifiedLogoPath != null) {
            model.addAttribute("logoPath", verifiedLogoPath);
        }
        model.addAttribute("companyId", company.getCompanyId());
        model.addAttribute("companyCode", session.getAttribute("verifiedCompanyCode"));
        model.addAttribute("companyDomain", company.getCompanyDomain());
        model.addAttribute("usesEmployeeNo", company.usesEmployeeNo());
        model.addAttribute("departments", deptService.findAll(company.getCompanyId()));
        model.addAttribute("positions", positionService.findAll(company.getCompanyId()));
        return "member/signup";
    }

    /** 회사별 회원가입 처리. */
    @PostMapping("/{companyDomain}/signup")
    public String newMember(@PathVariable String companyDomain, MemberDto dto) {
        MemberType result = memberService.join(dto);

        switch (result) {
            case JOIN_SUCCESS:
                // 가입 성공 → 같은 회사 로그인 페이지로.
                return "redirect:/" + companyDomain + "/login";
            case NOT_COMPANY:
            case ALREADY_MEMBER:
            case NOT_MATCH_PASSWORD:
            default:
                return "redirect:/" + companyDomain + "/signup?error=" + result.name();
        }
    }
}
