package com.team.intranet.config;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import com.team.intranet.entity.Company;
import com.team.intranet.repository.CompanyRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 일반 회원 로그아웃 성공 핸들러 — 로그아웃 직후 그 회원의 회사 로그인 페이지로 보낸다.
 *  - 1순위: Authentication.details(=CompanyAwareWebAuthenticationDetails) 의 companyId 로 회사 도메인 조회.
 *  - 2순위(폴백): Referer 헤더에서 `/{companyDomain}/...` 패턴 추출 — devtools 재시작/세션 만료로
 *    authentication 이 null 일 때도 사용자가 보던 회사 도메인을 유지하기 위함.
 *  - 모두 실패하면 회사 선택 랜딩(/company-login) 으로 폴백.
 */
@Component
@RequiredArgsConstructor
public class MemberLogoutSuccessHandler implements LogoutSuccessHandler {

    /** 예약 경로 — Referer 가 가리키는 첫 세그먼트가 이 중 하나면 companyDomain 으로 오인하면 안 됨. */
    private static final java.util.Set<String> RESERVED_FIRST_SEGMENTS = java.util.Set.of(
            "admin", "subAdmin", "subadmin", "master", "api", "member",
            "company-login", "error", "index", "calendar", "profile", "settings",
            "uploads", "css", "js", "images", "favicon.ico", "maintenance");

    /** Referer 의 path 첫 세그먼트(회사 도메인) 추출용. http(s)://host[:port]/{seg}/... */
    private static final Pattern REFERER_FIRST_SEG = Pattern.compile("^https?://[^/]+/([^/?#]+)");

    private final CompanyRepository companyRepository;

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        // 우선순위: ① 살아있는 인증 정보 → ② 로그인 시 박은 lastCompanyDomain 쿠키 → ③ Referer 헤더.
        // 자동 로그아웃(세션 만료/devtools 재시작) 시 authentication 이 null 이라 ②③ 폴백이 핵심.
        String domain = resolveCompanyDomain(authentication);
        if (domain == null || domain.isBlank()) {
            domain = resolveDomainFromCookie(request);
        }
        if (domain == null || domain.isBlank()) {
            domain = resolveDomainFromReferer(request);
        }
        if (domain != null && !domain.isBlank()) {
            response.sendRedirect(request.getContextPath() + "/" + domain + "/login");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/company-login");
    }

    /** LoginSuccessHandler 가 박은 lastCompanyDomain 쿠키에서 회사 도메인 추출. */
    private String resolveDomainFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (jakarta.servlet.http.Cookie c : request.getCookies()) {
            if (LoginSuccessHandler.COMPANY_DOMAIN_COOKIE.equals(c.getName())) {
                String v = c.getValue();
                if (v == null || v.isBlank()) return null;
                // 실제 회사가 존재하는지 한 번 더 확인 — 회사가 삭제된 경우 대비.
                return companyRepository.findByCompanyDomainIgnoreCase(v)
                        .map(Company::getCompanyDomain)
                        .orElse(null);
            }
        }
        return null;
    }

    private String resolveCompanyDomain(Authentication authentication) {
        if (authentication == null) return null;
        Object details = authentication.getDetails();
        if (!(details instanceof CompanyAwareWebAuthenticationDetails companyAware)) return null;
        Long companyId = companyAware.getCompanyId();
        if (companyId == null) return null;
        return companyRepository.findById(companyId)
                .map(Company::getCompanyDomain)
                .orElse(null);
    }

    /**
     * Referer 헤더에서 `/{companyDomain}/...` 패턴의 첫 세그먼트를 뽑아 DB 에 존재하는지 확인.
     *  - admin/master/api 같은 예약어는 회사 도메인이 아니므로 제외.
     *  - 회사가 실제 존재하지 않으면 null.
     */
    private String resolveDomainFromReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) return null;
        Matcher m = REFERER_FIRST_SEG.matcher(referer);
        if (!m.find()) return null;
        String candidate = m.group(1);
        if (candidate == null || candidate.isBlank()) return null;
        if (RESERVED_FIRST_SEGMENTS.contains(candidate.toLowerCase())) return null;
        // 실제 회사 존재 여부 확인 — 임의의 segment 가 들어오면 무시.
        return companyRepository.findByCompanyDomainIgnoreCase(candidate)
                .map(Company::getCompanyDomain)
                .orElse(null);
    }
}
