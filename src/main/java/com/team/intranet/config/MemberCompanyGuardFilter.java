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
        //  - 기존 세션 무효화 후, 새 세션에 안내 메시지를 담아 로그인 화면이 읽도록 한다.
        session.invalidate();
        SecurityContextHolder.clearContext();
        HttpSession fresh = request.getSession(true);
        fresh.setAttribute("loginError", "회사가 비활성화되었습니다. 관리자에게 문의하세요.");
        response.sendRedirect("/member/login");
    }

    /** 정적 리소스·로그인/로그아웃·에러 등 검사 제외 경로. */
    private boolean isExempt(String uri) {
        return uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/")
                || uri.startsWith("/uploads/") || uri.startsWith("/api/uploads/")
                || uri.startsWith("/api/company/")
                || uri.equals("/favicon.ico") || uri.equals("/error")
                || uri.equals("/member/login") || uri.equals("/member/logout");
    }
}
