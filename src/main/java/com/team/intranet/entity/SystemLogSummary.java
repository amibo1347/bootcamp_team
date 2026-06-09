package com.team.intranet.entity;

import java.time.LocalDateTime;

import com.team.intranet.enums.SystemLogPeriodType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시스템 로그 AI 요약 — 보관 기간 경과한 raw 로그를 일/월/분기 단위로 압축한 결과.
 *  - 계층:  raw(0~90일) → DAY(90일~1년) → MONTH(1~3년) → QUARTER(3년+)
 *  - summary 는 LLM 이 생성한 5~10줄 한국어 자연어. raw 행 수(rawCount) 는 함께 보존 — 신뢰 가능한 숫자.
 *  - 회사 격리 + (company_id, period_start) 인덱스 — 기간 조회 빠르게.
 */
@Entity
@Table(name = "tbl_system_log_summary",
    indexes = {
        @Index(name = "idx_log_summary_company_period",
               columnList = "company_id, period_type, period_start")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SystemLogSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "summary_id")
    private Long summaryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "period_type", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private SystemLogPeriodType periodType;

    /** 구간 시작 (포함). DAY 면 그 날 00:00, MONTH 면 1일 00:00, QUARTER 면 분기 첫째 달 1일. */
    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    /** 구간 끝 (배타). DAY = +1일, MONTH = +1달, QUARTER = +3달. */
    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    /** AI 가 생성한 자연어 요약 — 5~10줄 정도. */
    @Lob
    @Column(name = "summary", nullable = false)
    private String summary;

    /** 이 요약이 압축한 원본 row 수 — 신뢰 가능한 숫자(요약 정확도 보완용). */
    @Column(name = "raw_count", nullable = false)
    private long rawCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
