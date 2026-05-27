package com.team.intranet.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.SystemLogSummary;
import com.team.intranet.enums.SystemLogPeriodType;

/**
 * 시스템 로그 요약 조회/정리.
 *  - ADMIN 페이지: 회사 + period_type 조합으로 목록 페이징.
 *  - 스케줄러: 1년 경과 DAY 요약을 모아 MONTH 요약으로 재압축 → DAY 삭제. 3년 경과 MONTH 도 동일.
 */
public interface SystemLogSummaryRepository extends JpaRepository<SystemLogSummary, Long> {

    Page<SystemLogSummary> findByCompany_CompanyIdAndPeriodTypeOrderByPeriodStartDesc(
            Long companyId, SystemLogPeriodType periodType, Pageable pageable);

    List<SystemLogSummary> findByCompany_CompanyIdAndPeriodTypeAndPeriodEndLessThanOrderByPeriodStartAsc(
            Long companyId, SystemLogPeriodType periodType, LocalDateTime cutoff);

    @Modifying
    @Query("DELETE FROM SystemLogSummary s WHERE s.company.companyId = :companyId AND s.periodType = :type AND s.periodEnd < :cutoff")
    int deleteByCompanyAndTypeAndPeriodEndBefore(@Param("companyId") Long companyId,
                                                 @Param("type") SystemLogPeriodType type,
                                                 @Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT DISTINCT s.company.companyId FROM SystemLogSummary s WHERE s.periodType = :type AND s.periodEnd < :cutoff")
    List<Long> findCompanyIdsWithSummariesBefore(@Param("type") SystemLogPeriodType type,
                                                 @Param("cutoff") LocalDateTime cutoff);
}
