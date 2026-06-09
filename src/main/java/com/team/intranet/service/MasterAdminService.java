package com.team.intranet.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.entity.MasterAdmin;
import com.team.intranet.repository.MasterAdminRepository;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 계정 관리 (목록 / 생성).
 *  - MASTER 만 MASTER 를 만들 수 있다 — 접근 제어는 masterSecurityFilterChain + MasterTotpGuardFilter 가 담당.
 *  - 생성된 계정은 totpSecret 이 null → 팀원이 최초 로그인 시 본인 인증기를 직접 등록한다.
 *  - 본인 계정 관리(비밀번호 변경)는 MasterAccountService 가 별도로 담당.
 */
@Service
@RequiredArgsConstructor
public class MasterAdminService {

    private final MasterAdminRepository masterAdminRepository;
    private final PasswordEncoder passwordEncoder;

    /** 전체 MASTER 계정 목록 (생성 순). */
    @Transactional(readOnly = true)
    public List<MasterAdmin> findAll() {
        return masterAdminRepository.findAll(Sort.by(Sort.Direction.ASC, "masterAdminId"));
    }

    /**
     * 새 MASTER 계정 생성.
     * @param rawPassword 평문 초기 비밀번호 — 인코딩해 저장한다. 팀원이 로그인 후 직접 변경.
     * @throws IllegalArgumentException 아이디가 이미 사용 중인 경우 (호출자가 사용자 메시지로 사용)
     */
    @Transactional
    public void create(String loginId, String name, String email, String rawPassword) {
        String normalizedLoginId = loginId.trim();
        if (masterAdminRepository.existsByLoginIdIgnoreCase(normalizedLoginId)) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다: " + normalizedLoginId);
        }

        MasterAdmin master = MasterAdmin.create(
                normalizedLoginId,
                passwordEncoder.encode(rawPassword),
                name.trim(),
                (email == null || email.isBlank()) ? null : email.trim());
        masterAdminRepository.save(master);
    }
}
