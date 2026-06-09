package com.team.intranet.config;

/**
 * Guard 필터들에서 공통적으로 검사하는 URI 분류 헬퍼.
 *  - MemberCompanyGuardFilter / SystemMaintenanceGuardFilter 양쪽에서 같은 정적·로그아웃·에러 경로를
 *    매번 if 체인으로 반복하던 것을 한 곳에 모은다.
 *  - 각 필터의 고유한 면제 경로(/company-login, /maintenance 등)는 호출 측에 남긴다.
 */
final class GuardFilters {

    private GuardFilters() {}

    /** AJAX/REST 요청 여부 — /api/ 로 시작하면 fetch 등 비대화형 호출로 간주. */
    static boolean isApiRequest(String uri) {
        return uri != null && uri.startsWith("/api/");
    }

    /** 어떤 가드 필터든 통과시켜야 하는 공통 경로: 정적 리소스 + favicon + 에러 페이지 + 로그아웃. */
    static boolean isCommonExempt(String uri) {
        if (uri == null) return false;
        return uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/images/")
                || uri.equals("/favicon.ico")
                || uri.equals("/error")
                || uri.equals("/member/logout");
    }
}
