package com.team.intranet.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.SystemLog;

/**
 * 시스템 로그 raw 조회/정리.
 *  - ADMIN 페이지: 회사 + 기간 페이징.
 *  - 스케줄러: 90일 경과 row 를 일별로 모아 요약 → raw 삭제.
 */
public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {

    /** 회사 로그 페이지 — 최신순. */
    Page<SystemLog> findByCompany_CompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    /** 회사 + 기간 범위 페이지 — 최신순. */
    Page<SystemLog> findByCompany_CompanyIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long companyId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    /** 회사 + 시각 < cutoff 인 row 전체 — 스케줄러 요약 대상. */
    List<SystemLog> findByCompany_CompanyIdAndCreatedAtLessThanOrderByCreatedAtAsc(
            Long companyId, LocalDateTime cutoff);

    /** 시각 < cutoff 인 raw 일괄 삭제 — 요약 저장 직후 호출. */
    @Modifying
    @Query("DELETE FROM SystemLog l WHERE l.company.companyId = :companyId AND l.createdAt < :cutoff")
    int deleteByCompanyAndCreatedAtBefore(@Param("companyId") Long companyId,
                                          @Param("cutoff") LocalDateTime cutoff);

    /** 회사 목록 — 요약 대상 회사를 빠르게 알기 위한 distinct(스케줄러용). */
    @Query("SELECT DISTINCT l.company.companyId FROM SystemLog l WHERE l.createdAt < :cutoff")
    List<Long> findCompanyIdsWithLogsBefore(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 회사별 가장 최근 SystemLog 시각 (=마지막 관리자 활동 시점).
     *  - MASTER 사용량 대시보드의 "마지막 활동" 컬럼.
     *  - 한 회사에 로그가 한 건도 없으면 row 자체가 안 나오므로 호출 측에서 null 처리.
     *  - Object[] 결과: [0]=companyId(Long), [1]=lastAt(LocalDateTime)
     */
    @Query("SELECT l.company.companyId, MAX(l.createdAt) FROM SystemLog l GROUP BY l.company.companyId")
    List<Object[]> findLastActivityPerCompany();
}
