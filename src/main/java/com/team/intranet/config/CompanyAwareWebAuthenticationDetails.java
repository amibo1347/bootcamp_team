package com.team.intranet.config;

import org.springframework.security.web.authentication.WebAuthenticationDetails;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 로그인 폼이 hidden 필드(companyId)로 함께 보낸 회사 식별자를 인증 컨텍스트에 싣는 details.
 *  - 일반 회원 로그인은 loginId 가 회사별 스코프이므로, 회원을 특정하려면 companyId 가 필요하다.
 *  - 표준 WebAuthenticationDetails(원격주소/세션ID)를 확장해 companyId 하나만 더 보관한다.
 */
public class CompanyAwareWebAuthenticationDetails extends WebAuthenticationDetails {

    private final Long companyId;

    public CompanyAwareWebAuthenticationDetails(HttpServletRequest request) {
        super(request);
        this.companyId = parseCompanyId(request.getParameter("companyId"));
    }

    public Long getCompanyId() {
        return companyId;
    }

    private static Long parseCompanyId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
