package com.team.intranet.config;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.team.intranet.enums.IsActive;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.session.MemberSession;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 비활성 회사 소속 회원 자동 로그아웃 게이트.
 *  - 로그인 중인 회원의 소속 회사가 (MASTER 에 의해) 비활성화되면, 다음 요청 시 강제 로그아웃한다.
 *  - 일반 회원 보안 체인(securityFilterChain)에만 등록 → MASTER 화면에는 영향 없음.
 *    전역 자동 등록을 피하려고 @Component 가 아니라 SecurityConfig 에서 직접 생성한다.
 */
public class MemberCompanyGuardFilter extends OncePerRequestFilter {

    private final CompanyRepository companyRepository;

    public MemberCompanyGuardFilter(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (isExempt(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("memberSession") instanceof MemberSession ms)) {
            chain.doFilter(request, response); // 로그인한 회원이 아님 (비로그인 / MASTER 등)
            return;
        }

        Long companyId = ms.getCompanyId();
        if (companyId == null || companyRepository.existsByCompanyIdAndIsActive(companyId, IsActive.Y)) {
            chain.doFilter(request, response); // 회사가 여전히 활성
            return;
        }

        // 소속 회사가 비활성 → 강제 로그아웃.
        session.invalidate();
        SecurityContextHolder.clearContext();

        if (isApiRequest(request)) {
            // AJAX/API 요청: 302 리다이렉트는 fetch 가 조용히 삼켜 화면이 안 바뀐다.
            //  → 401 + X-Logout-Reason 헤더로 알려, 프론트 공통 가드(api-error.js)가
            //    어떤 행동(AJAX 포함)이든 즉시 로그인 화면으로 보내도록 한다.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Logout-Reason", "company-inactive");
        } else {
            // 일반 페이지 요청: 로그인 화면으로 리다이렉트 + 안내 메시지.
            HttpSession fresh = request.getSession(true);
            fresh.setAttribute("loginError", "회사가 비활성화되었습니다. 관리자에게 문의하세요.");
            response.sendRedirect("/company-login");
        }
    }

    /** AJAX/API 요청 여부 — /api/ 로 시작하면 fetch 호출로 간주. */
    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    /** 정적 리소스·로그인/로그아웃·에러 등 검사 제외 경로. */
    private boolean isExempt(String uri) {
        return uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/")
                || uri.startsWith("/api/company/")
                || uri.equals("/favicon.ico") || uri.equals("/error")
                // 회사 선택 랜딩 + 회사별 로그인/회원가입 페이지 모두 제외
                || uri.equals("/company-login") || uri.endsWith("/login")
                || uri.endsWith("/signup") || uri.equals("/member/logout");
    }
}
