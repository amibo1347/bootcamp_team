package com.team.intranet.config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private static final String DEFAULT_MESSAGE = "아이디 또는 비밀번호가 일치하지 않습니다.";

    /**
     * Spring Security 예외 → 사용자 안내 메시지 매핑.
     * LinkedHashMap 으로 선언 순서대로 lookup (현재는 클래스 매칭 1:1 이라 순서 무관하지만 가독성 위해).
     */
    private static final Map<Class<? extends AuthenticationException>, String> MESSAGES = new LinkedHashMap<>();
    static {
        MESSAGES.put(DisabledException.class,           "관리자 승인이 필요한 계정입니다.");
        MESSAGES.put(LockedException.class,             "가입이 반려된 계정입니다. 관리자에게 문의하세요.");
        MESSAGES.put(AccountExpiredException.class,     "탈퇴된 계정입니다. 관리자에게 문의하세요.");
        MESSAGES.put(CredentialsExpiredException.class, "회사가 비활성화되었습니다. 관리자에게 문의하세요.");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        String errorMessage = MESSAGES.getOrDefault(exception.getClass(), DEFAULT_MESSAGE);
        request.getSession().setAttribute("loginError", errorMessage);

        // 로그인 폼 hidden 필드(companyDomain)로 어느 회사 로그인 페이지였는지 알 수 있으면 그곳으로 되돌린다.
        String companyDomain = request.getParameter("companyDomain");
        if (companyDomain != null && !companyDomain.isBlank()) {
            response.sendRedirect("/" + companyDomain.trim() + "/login");
        } else {
            response.sendRedirect("/company-login");
        }
    }
}
