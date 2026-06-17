package com.team.intranet.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;

import java.io.IOException;

/**
 * 동시 로그인으로 기존 세션이 강제 만료(다른 곳에서 같은 계정 로그인)됐을 때의 응답 전략.
 *
 * <p>정책(A안): 새 로그인을 허용하고 기존 세션을 만료시킨다. 만료된 기존 기기가 다음 요청을
 * 보내면 이 전략이 동작한다.
 * <ul>
 *   <li>API 요청(/api/**): 302 redirect 는 fetch 가 처리하기 곤란하므로 <b>401 JSON</b> 으로 응답.</li>
 *   <li>그 외 페이지 요청: 소속 회사 로그인 페이지(/{domain}/login?expired) 로 redirect.
 *       lastCompanyDomain 쿠키가 없으면 /company-login?expired 로 폴백.</li>
 * </ul>
 */
public class MemberSessionExpiredStrategy implements SessionInformationExpiredStrategy {

    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
        HttpServletRequest request = event.getRequest();
        HttpServletResponse response = event.getResponse();

        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"SESSION_EXPIRED\","
                    + "\"message\":\"다른 곳에서 로그인되어 현재 세션이 종료되었습니다.\"}");
            return;
        }

        String domain = readCompanyDomainCookie(request);
        String target = (domain != null && !domain.isBlank())
                ? "/" + domain + "/login?expired"
                : "/company-login?expired";
        response.sendRedirect(target);
    }

    private String readCompanyDomainCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (LoginSuccessHandler.COMPANY_DOMAIN_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
