package com.team.intranet.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.SystemNotice;

public interface SystemNoticeRepository extends JpaRepository<SystemNotice, Long> {

    /** 지정 시각에 노출 대상인 공지 — 최신 시작 순. */
    @Query("SELECT n FROM SystemNotice n "
         + "WHERE n.startsAt <= :now AND (n.endsAt IS NULL OR n.endsAt >= :now) "
         + "ORDER BY n.startsAt DESC")
    List<SystemNotice> findActive(@Param("now") LocalDateTime now);
}
