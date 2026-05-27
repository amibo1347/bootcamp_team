package com.team.intranet.config;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.team.intranet.entity.Company;
import com.team.intranet.repository.CompanyRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 비인증 진입 시 로그인 페이지로 보내는 진입점.
 *  - Spring Security 기본은 SecurityConfig 의 loginPage("/company-login") 로 보냄 → 자동 로그아웃 시
 *    회사 도메인 정보가 없어 회사 선택 랜딩으로 떨어진다.
 *  - 이 진입점은 lastCompanyDomain 쿠키 (LoginSuccessHandler 가 박음) 를 보고 `/{domain}/login` 으로 redirect.
 *  - 쿠키가 없거나 회사가 사라졌으면 /company-login 으로 폴백.
 *  - API 요청(`/api/**`)은 redirect 대신 401 + X-Logout-Reason 헤더 → 프론트 가드가 처리.
 */
@Component
@RequiredArgsConstructor
public class CompanyAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final CompanyRepository companyRepository;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        String uri = request.getRequestURI();

        // API 요청은 redirect 가 안 통함 — 401 응답 + 헤더로 프론트가 처리.
        if (uri != null && uri.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Logout-Reason", "auth-required");
            return;
        }

        String domain = resolveDomainFromCookie(request);
        if (domain != null && !domain.isBlank()) {
            response.sendRedirect(request.getContextPath() + "/" + domain + "/login");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/company-login");
    }

    private String resolveDomainFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (LoginSuccessHandler.COMPANY_DOMAIN_COOKIE.equals(c.getName())) {
                String v = c.getValue();
                if (v == null || v.isBlank()) return null;
                return companyRepository.findByCompanyDomainIgnoreCase(v)
                        .map(Company::getCompanyDomain)
                        .orElse(null);
            }
        }
        return null;
    }
}
