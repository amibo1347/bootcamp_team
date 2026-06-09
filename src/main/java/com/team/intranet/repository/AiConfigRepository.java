package com.team.intranet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.AiConfig;

public interface AiConfigRepository extends JpaRepository<AiConfig, Long> {

    /** Singleton 1 row 가정 — 가장 작은 ID 1개. */
    Optional<AiConfig> findFirstByOrderByConfigIdAsc();
}
