package com.team.intranet.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.entity.MasterAdmin;
import com.team.intranet.repository.MasterAdminRepository;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 본인 계정 관리.
 *  - 현재는 비밀번호 변경만. (인수인계·타 MASTER 관리는 후속 PR)
 */
@Service
@RequiredArgsConstructor
public class MasterAccountService {

    private final MasterAdminRepository masterAdminRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 본인 비밀번호 변경.
     *  - 현재 비밀번호가 일치해야 한다. 새 비밀번호는 인코딩해 저장.
     * @throws IllegalArgumentException 계정이 없거나 현재 비밀번호가 틀린 경우 (호출자가 사용자 메시지로 사용)
     */
    @Transactional
    public void changePassword(Long masterAdminId, String currentRawPassword, String newRawPassword) {
        MasterAdmin master = masterAdminRepository.findById(masterAdminId)
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));

        if (!passwordEncoder.matches(currentRawPassword, master.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        master.changePassword(passwordEncoder.encode(newRawPassword));
        masterAdminRepository.save(master);
    }
}
