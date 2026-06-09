package com.team.intranet.config;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MASTER 비밀번호 인증 성공 처리 (1차).
 *  - 아직 콘솔 진입을 허용하지 않는다 — TOTP 2차 인증이 남았다.
 *  - 통과한 loginId 를 세션에 "pending" 으로만 표시하고 TOTP 단계로 보낸다.
 *  - 최종 진입(masterSession 부여)은 TOTP 검증 성공 시 MasterTotpController 가 처리.
 */
@Component
public class MasterLoginSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        request.getSession().setAttribute("masterPendingLoginId", authentication.getName());
        response.sendRedirect("/master/totp");
    }
}
