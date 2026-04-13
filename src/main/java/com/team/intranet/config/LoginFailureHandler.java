package com.team.intranet.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, 
                                        AuthenticationException exception) throws IOException {
        
        String message = "아이디 또는 비밀번호가 일치하지 않습니다.";

        if (exception instanceof DisabledException) {
            message = "관리자 승인이 필요한 계정입니다.";
        }

        // 한글 깨짐 방지를 위해 인코딩
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
        response.sendRedirect("/member/login?error=true&message=" + encodedMessage);
    }
}