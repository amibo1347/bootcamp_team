package com.team.intranet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.entity.MasterAdmin;
import com.team.intranet.repository.MasterAdminRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 최초 MASTER 계정 부트스트랩.
 *  - MASTER 는 MASTER 만 생성 가능 → 최초 1개는 부팅 시 자동 생성해야 닭-달걀 문제가 풀린다.
 *  - tbl_master_admin 이 비어 있을 때만 1회 동작. 이미 계정이 있으면 아무것도 하지 않는다.
 *  - 계정 정보는 application.properties 의 master.bootstrap.* 로 주입한다.
 *  - 생성되는 기본 비밀번호는 최초 로그인용 — 로그인 후 즉시 변경할 것.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterAdminInitializer implements ApplicationRunner {

    private final MasterAdminRepository masterAdminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${master.bootstrap.enabled:true}")
    private boolean enabled;

    @Value("${master.bootstrap.login-id:master}")
    private String loginId;

    @Value("${master.bootstrap.password:}")
    private String rawPassword;

    @Value("${master.bootstrap.name:시스템 관리자}")
    private String name;

    @Value("${master.bootstrap.email:}")
    private String email;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (masterAdminRepository.count() > 0) return; // 이미 MASTER 존재 → skip

        if (rawPassword == null || rawPassword.isBlank()) {
            log.warn("MASTER 계정이 없지만 master.bootstrap.password 가 비어 있어 최초 계정을 생성하지 못했습니다.");
            return;
        }

        MasterAdmin master = MasterAdmin.create(
            loginId,
            passwordEncoder.encode(rawPassword),
            name,
            (email == null || email.isBlank()) ? null : email);
        masterAdminRepository.save(master);

        log.info("최초 MASTER 계정을 생성했습니다. loginId={} — 로그인 후 비밀번호를 즉시 변경하세요.", loginId);
    }
}
