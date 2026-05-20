package com.team.intranet.config;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.team.intranet.entity.Member;
import com.team.intranet.enums.IsActive;
import com.team.intranet.enums.member.Status;
import com.team.intranet.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        // 1. DB에서 아이디로 회원 조회
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 사용자입니다."));

        // 3. 시큐리티 전용 User 객체 생성 (아이디, 암호화된 비번, 권한)
        // role 은 익명화 시 null 일 수 있으나, 그 전에 LEAVE/BANNED 가 잡혀 차단되므로
        // 실제 인증 흐름에서는 도달하지 않음. 방어적으로 USER 폴백.
        String roleName = member.getRole() != null ? member.getRole().name() : "USER";
        // 소속 회사가 비활성(IsActive.N) 이면 로그인 차단.
        //  - 사용 가능한 4개 플래그 중 credentialsExpired 만 비어 있어 이를 사용 (CredentialsExpiredException).
        boolean companyInactive = member.getCompany() != null
                && member.getCompany().getIsActive() == IsActive.N;
        return User.builder()
                .username(member.getLoginId())
          .password(member.getPassword())
          .roles(roleName)
          .disabled(member.getStatus() == Status.WAIT)
          .accountLocked(member.getStatus() == Status.REJECT) // 가입 반려 안내용
          .accountExpired(member.getStatus() == Status.LEAVE || member.getStatus() == Status.BANNED) // 탈퇴/해고 모두 '탈퇴된 계정' 안내
          .credentialsExpired(companyInactive) // 비활성 회사 소속 → 로그인 차단
          .build();
    }
}