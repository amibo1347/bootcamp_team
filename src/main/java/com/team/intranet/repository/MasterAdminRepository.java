package com.team.intranet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.MasterAdmin;

public interface MasterAdminRepository extends JpaRepository<MasterAdmin, Long> {

    /** 로그인 아이디로 조회 (대소문자 무관). 인증에 사용. */
    Optional<MasterAdmin> findByLoginIdIgnoreCase(String loginId);

    boolean existsByLoginIdIgnoreCase(String loginId);
}
