package com.team.intranet.config;

import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;

/**
 * 일반 회원 인증 주체(principal).
 *
 * <p>동시 세션 제어(maximumSessions)는 SessionRegistry 가 principal 의 equals/hashCode 로
 * "같은 사용자"를 묶어 세션 수를 센다. 기존처럼 principal 을 loginId(String) 로 두면
 * loginId 는 <b>회사별로만</b> 유니크하므로, 회사가 다른 동일 사번/아이디 사용자끼리
 * 같은 사용자로 오인되어 서로를 강제 로그아웃시키는 멀티테넌트 버그가 생긴다.
 *
 * <p>따라서 전역 유일키인 {@code memberId} 로 동일성을 판단한다.
 * {@link #getName()} 은 {@code authentication.getName()} 을 loginId 로 기대하는
 * 기존 코드(LoginSuccessHandler 등)와의 호환을 위해 loginId 를 반환한다.
 */
public final class MemberPrincipal implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    private final Long memberId;   // 전역 유일 식별자 — 동일성 판단 기준
    private final Long companyId;  // 소속 회사 (참고용)
    private final String loginId;  // 회사 스코프 로그인 아이디 — 표시/호환용

    public MemberPrincipal(Long memberId, Long companyId, String loginId) {
        this.memberId = memberId;
        this.companyId = companyId;
        this.loginId = loginId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getLoginId() {
        return loginId;
    }

    /** authentication.getName() 호환 — 기존처럼 loginId 를 돌려준다. */
    @Override
    public String getName() {
        return loginId;
    }

    /** 동일성은 전역 유일키 memberId 로만 판단 (회사별 loginId 충돌 회피). */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberPrincipal that)) return false;
        return Objects.equals(memberId, that.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(memberId);
    }

    @Override
    public String toString() {
        return loginId;
    }
}
