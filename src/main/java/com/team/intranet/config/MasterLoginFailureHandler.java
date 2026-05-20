package com.team.intranet.config;

import java.io.IOException;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MASTER 로그인 실패 처리.
 *  - 실패 사유를 세션("masterLoginError")에 담아 /master/login 으로 리다이렉트.
 */
@Component
public class MasterLoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        String errorMessage = "아이디 또는 비밀번호가 일치하지 않습니다.";
        if (exception instanceof DisabledException) {
            errorMessage = "비활성화된 MASTER 계정입니다. 다른 MASTER 에게 문의하세요.";
        }

        request.getSession().setAttribute("masterLoginError", errorMessage);
        response.sendRedirect("/master/login");
    }
}
