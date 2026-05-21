package com.team.intranet.config;

import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 일반 회원 form-login 시 details 를 CompanyAwareWebAuthenticationDetails 로 만든다.
 *  - SecurityConfig 의 formLogin().authenticationDetailsSource(...) 에 등록.
 */
public class CompanyAwareAuthenticationDetailsSource
        implements AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> {

    @Override
    public WebAuthenticationDetails buildDetails(HttpServletRequest request) {
        return new CompanyAwareWebAuthenticationDetails(request);
    }
}
