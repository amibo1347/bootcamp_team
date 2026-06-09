package com.team.intranet.config;

import com.team.intranet.entity.Company;
import com.team.intranet.entity.Member;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.session.MemberSession;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    /** 회사 도메인 쿠키 이름. 세션 만료/로그아웃 후 redirect 길잡이로 사용. */
    public static final String COMPANY_DOMAIN_COOKIE = "lastCompanyDomain";
    /** 쿠키 유효 기간 — 30일. (회원이 같은 PC 에서 다시 로그인할 때까지의 길잡이로 충분.) */
    private static final int COMPANY_DOMAIN_COOKIE_MAX_AGE_SEC = 60 * 60 * 24 * 30;

    private final MemberRepository memberRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        // 1. 로그인한 사용자 정보 가져오기 — loginId 는 회사별 스코프이므로 companyId 와 함께 조회.
        String loginId = authentication.getName();
        Long companyId = (authentication.getDetails() instanceof CompanyAwareWebAuthenticationDetails details)
                ? details.getCompanyId() : null;
        Member member = memberRepository.findByCompany_CompanyIdAndLoginId(companyId, loginId).orElseThrow();

        // 2. 태호님이 만든 MemberSession 객체 생성 및 세션 저장
        MemberSession ms = new MemberSession(member);
        HttpSession session = request.getSession();
        session.setAttribute("memberSession", ms);

        // 3. 회사 도메인 쿠키 발급 — 세션이 만료되거나 devtools 재시작으로 JSESSIONID 가 사라져도
        //    AuthenticationEntryPoint / LogoutSuccessHandler 가 이 쿠키를 보고 /{domain}/login 으로 보낸다.
        //    (없으면 /company-login 폴백)
        Company company = member.getCompany();
        if (company != null && company.getCompanyDomain() != null && !company.getCompanyDomain().isBlank()) {
            Cookie cookie = new Cookie(COMPANY_DOMAIN_COOKIE, company.getCompanyDomain());
            cookie.setPath("/");
            cookie.setMaxAge(COMPANY_DOMAIN_COOKIE_MAX_AGE_SEC);
            cookie.setHttpOnly(true);
            response.addCookie(cookie);
        }

        // 4. 성공 후 메인 페이지로 이동
        response.sendRedirect("/index");
    }
}