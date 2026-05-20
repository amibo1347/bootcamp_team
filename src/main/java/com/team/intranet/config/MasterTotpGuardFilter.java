package com.team.intranet.config;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * MASTER 2차 인증(TOTP) 게이트.
 *  - 비밀번호 인증은 통과(= hasRole("MASTER"))했으나 TOTP 검증을 아직 안 끝낸 요청을 막는다.
 *  - TOTP 통과 시점에만 세션에 "masterSession" 이 생기므로, 그 존재 여부로 판정한다.
 *  - masterSecurityFilterChain 에만 등록되므로 일반 회원 페이지에는 영향이 없다.
 *    (전역 자동 등록을 피하려고 @Component 로 만들지 않고 SecurityConfig 에서 직접 생성한다.)
 */
public class MasterTotpGuardFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        // 게이트 예외: 로그인/로그아웃/TOTP 화면 자체, 그리고 (현재 미사용인) /api/master/**.
        if (uri.equals("/master/login") || uri.equals("/master/logout")
                || uri.equals("/master/totp") || uri.startsWith("/master/totp/")
                || uri.startsWith("/api/master/")) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);

        // TOTP 까지 통과 → masterSession 존재 → 통과.
        if (session != null && session.getAttribute("masterSession") != null) {
            chain.doFilter(request, response);
            return;
        }

        // 비밀번호는 통과했으나 TOTP 미완료 → TOTP 화면으로. pending 도 없으면 로그인부터.
        boolean hasPending = session != null && session.getAttribute("masterPendingLoginId") != null;
        response.sendRedirect(hasPending ? "/master/totp" : "/master/login");
    }
}
