package com.team.intranet.config;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import com.team.intranet.service.SystemMaintenanceService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 시스템 점검 모드 게이트.
 *  - 점검 ON 중에는 일반 회원의 요청을 차단 (페이지: /maintenance 로 리다이렉트, API: 503 + 헤더).
 *  - MASTER 콘솔(/master/**, /api/master/**) 은 별도 SecurityFilterChain(@Order(1)) 이 처리하므로
 *    이 필터는 일반 회원 체인에만 등록되어 자동으로 MASTER 영향 X.
 *  - 점검 안내 페이지(/maintenance) 와 점검 상태 폴링(/api/system-maintenance/**) 은 통과.
 *  - 정적 리소스·로그아웃·에러는 통과 — 점검 페이지가 렌더되려면 CSS/JS/이미지가 살아있어야 한다.
 *
 *  ※ 전역 @Component 등록을 피하려고 SecurityConfig 가 직접 생성해서 한 체인에만 끼운다.
 */
public class SystemMaintenanceGuardFilter extends OncePerRequestFilter {

    private final SystemMaintenanceService maintenanceService;

    public SystemMaintenanceGuardFilter(SystemMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (isExempt(uri)) {
            chain.doFilter(request, response);
            return;
        }
        if (!maintenanceService.isCurrentlyOn()) {
            chain.doFilter(request, response);
            return;
        }

        // 점검 중. MASTER 콘솔은 별도 체인이라 여기 안 옴 → 그대로 차단.
        if (isApiRequest(uri)) {
            // AJAX/API: 503 Service Unavailable + 헤더 — 프론트 가드가 /maintenance 로 보낼 수 있도록.
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("X-Maintenance-Mode", "on");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"maintenance\"}");
            return;
        }
        // 페이지 요청: 점검 안내 페이지로 리다이렉트.
        response.sendRedirect("/maintenance");
    }

    private boolean isApiRequest(String uri) {
        return uri.startsWith("/api/");
    }

    /** 정적 리소스 / 점검 페이지 본인 / 폴링 API / 로그아웃·에러는 통과. */
    private boolean isExempt(String uri) {
        return uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/")
                || uri.equals("/favicon.ico") || uri.equals("/error")
                || uri.equals("/maintenance")
                || uri.startsWith("/api/system-maintenance/")
                || uri.equals("/member/logout"); // 점검 중에도 로그아웃은 가능해야 함
    }
}
