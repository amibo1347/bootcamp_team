package com.team.intranet.config;

import java.io.IOException;

import org.springframework.security.authentication.AccountExpiredException;
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

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, 
                                    AuthenticationException exception) throws IOException, ServletException {
    
    String errorMessage = "아이디 또는 비밀번호가 일치하지 않습니다.";

    if (exception instanceof DisabledException) {
        errorMessage = "관리자 승인이 필요한 계정입니다.";
    } else if (exception instanceof LockedException) {
        errorMessage = "가입이 반려된 계정입니다. 관리자에게 문의하세요.";
    } else if (exception instanceof AccountExpiredException) {
        errorMessage = "탈퇴된 계정입니다. 관리자에게 문의하세요.";
    } 
    // 💡 세션에 직접 메시지를 저장합니다.
    request.getSession().setAttribute("loginError", errorMessage);
    
    // 리다이렉트 (파라미터 없이 깔끔하게 보냅니다)
    response.sendRedirect("/member/login");
}
}