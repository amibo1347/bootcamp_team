package com.team.intranet.config;

import java.util.List;

import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.team.intranet.entity.Member;
import com.team.intranet.enums.IsActive;
import com.team.intranet.enums.member.Status;
import com.team.intranet.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 일반 회원 로그인 인증 — (회사 + loginId) 복합키로 회원을 특정한다.
 *  - loginId 는 회사별로만 유니크(회사가 다르면 같은 사번/아이디 허용)하므로,
 *    단일 username 만 받는 표준 UserDetailsService 로는 회원을 못 찾는다.
 *  - 회사 식별자(companyId)는 로그인 폼 hidden 필드로 전달되어
 *    CompanyAwareWebAuthenticationDetails(= authentication.getDetails())에 실려 온다.
 *  - 상태/회사활성 검사는 기존 CustomUserDetailsService 의 플래그(disabled/locked/expired)와 동일한 의미.
 */
@Component
@RequiredArgsConstructor
public class MemberAuthenticationProvider implements AuthenticationProvider {

    private static final String GENERIC_FAIL = "아이디 또는 비밀번호가 일치하지 않습니다.";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String loginId = authentication.getName();
        String rawPassword = authentication.getCredentials() == null
                ? "" : authentication.getCredentials().toString();
        Long companyId = resolveCompanyId(authentication);

        if (companyId == null || loginId == null || loginId.isBlank()) {
            throw new BadCredentialsException(GENERIC_FAIL);
        }

        Member member = memberRepository.findByCompany_CompanyIdAndLoginId(companyId, loginId.trim())
                .orElseThrow(() -> new BadCredentialsException(GENERIC_FAIL));

        // 상태 점검 (비밀번호 확인 전 — 기존 DaoAuthenticationProvider 의 pre-check 순서와 동일).
        Status status = member.getStatus();
        if (status == Status.WAIT) {
            throw new DisabledException("관리자 승인이 필요한 계정입니다.");
        }
        if (status == Status.REJECT) {
            throw new LockedException("가입이 반려된 계정입니다.");
        }
        if (status == Status.LEAVE || status == Status.BANNED) {
            throw new AccountExpiredException("탈퇴된 계정입니다.");
        }

        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new BadCredentialsException(GENERIC_FAIL);
        }

        // 소속 회사가 명시적으로 활성(Y)이 아니면 차단 (MemberCompanyGuardFilter 와 동일 기준).
        if (member.getCompany() == null || member.getCompany().getIsActive() != IsActive.Y) {
            throw new CredentialsExpiredException("회사가 비활성화되었습니다.");
        }

        String roleName = member.getRole() != null ? member.getRole().name() : "USER";
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleName));

        UsernamePasswordAuthenticationToken result =
                new UsernamePasswordAuthenticationToken(member.getLoginId(), null, authorities);
        // companyId 를 담은 details 를 그대로 넘겨 LoginSuccessHandler 가 회원을 다시 특정할 수 있게 한다.
        result.setDetails(authentication.getDetails());
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private Long resolveCompanyId(Authentication authentication) {
        if (authentication.getDetails() instanceof CompanyAwareWebAuthenticationDetails details) {
            return details.getCompanyId();
        }
        return null;
    }
}
