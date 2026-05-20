package com.team.intranet.config;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.team.intranet.entity.MasterAdmin;
import com.team.intranet.enums.IsActive;
import com.team.intranet.repository.MasterAdminRepository;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 계정 전용 UserDetailsService.
 *  - 일반 회원(CustomUserDetailsService)과 분리. /master/login 보안 체인에서만 사용한다.
 *  - isActive == N 이면 disabled 처리 → 로그인 시 DisabledException 으로 차단.
 */
@Service
@RequiredArgsConstructor
public class MasterUserDetailsService implements UserDetailsService {

    private final MasterAdminRepository masterAdminRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        MasterAdmin master = masterAdminRepository.findByLoginIdIgnoreCase(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 MASTER 계정입니다."));

        return User.builder()
                .username(master.getLoginId())
                .password(master.getPassword())
                .roles("MASTER")
                .disabled(master.getIsActive() == IsActive.N)
                .build();
    }
}
