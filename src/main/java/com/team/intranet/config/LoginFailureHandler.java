package com.team.intranet.config;

import jakarta.servlet.ServletException;
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
                                    AuthenticationException exception) throws IOException, ServletException {
    
    String errorMessage = "아이디 또는 비밀번호가 일치하지 않습니다.";

    if (exception instanceof DisabledException) {
        errorMessage = "관리자 승인이 필요한 계정입니다.";
    }

    // 💡 세션에 직접 메시지를 저장합니다.
    request.getSession().setAttribute("loginError", errorMessage);
    
    // 리다이렉트 (파라미터 없이 깔끔하게 보냅니다)
    response.sendRedirect("/member/login");
}
}