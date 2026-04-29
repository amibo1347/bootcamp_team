package com.team.intranet.config;

import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.team.intranet.entity.Member;
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
        return User.builder()
                .username(member.getLoginId())
          .password(member.getPassword())
          .roles(member.getRole().name())
          .disabled(member.getStatus() == Status.WAIT)     
          .accountLocked(member.getStatus() == Status.REJECT) 
          .accountExpired(member.getStatus() == Status.LEAVE) 
          .build();
    }
}