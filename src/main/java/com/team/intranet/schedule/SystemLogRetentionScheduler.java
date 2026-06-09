package com.team.intranet.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.dto.ai.LlmMessage;
import com.team.intranet.dto.ai.LlmResponse;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.SystemLog;
import com.team.intranet.entity.SystemLogSummary;
import com.team.intranet.enums.SystemLogPeriodType;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.SystemLogRepository;
import com.team.intranet.repository.SystemLogSummaryRepository;
import com.team.intranet.service.ai.LlmClientFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 시스템 로그 보관 정책 — 계층적 AI 요약.
 *
 * 단계:
 *  1) raw → DAY summary    : RAW_RETENTION_DAYS 일 이전 raw 로그를 일별로 묶어 LLM 요약 → raw 삭제.
 *  2) DAY → MONTH summary  : DAY_RETENTION_DAYS 일 이전 DAY 요약들을 월별로 재압축 → DAY 삭제.
 *  3) MONTH → QUARTER      : MONTH_RETENTION_DAYS 일 이전 MONTH 요약을 분기별로 재압축 → MONTH 삭제.
 *  4) QUARTER 삭제          : QUARTER_RETENTION_DAYS 일 경과한 분기 요약은 삭제(보관 종료).
 *
 * 실행: 매일 03:30 (다른 retention 스케줄러와 겹치지 않게).
 * 실패 정책: 회사 단위로 try/catch — 한 회사 요약이 실패해도 다음 회사로 진행.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemLogRetentionScheduler {

    /** raw 로그 → 일별 요약 압축 경계 (일). */
    private static final long RAW_RETENTION_DAYS = 90;
    /** 일별 → 월별 재압축 경계 (일). */
    private static final long DAY_RETENTION_DAYS = 365;
    /** 월별 → 분기별 재압축 경계 (일). */
    private static final long MONTH_RETENTION_DAYS = 365 * 3;
    /** 분기별 보관 종료 경계 (일). */
    private static final long QUARTER_RETENTION_DAYS = 365 * 5;

    /** 일/월 요약 LLM 호출 시 단일 그룹이 너무 크면 잘라낼 row 수 상한. 토큰 보호. */
    private static final int MAX_ROWS_PER_PROMPT = 300;

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm");

    private final SystemLogRepository logRepository;
    private final SystemLogSummaryRepository summaryRepository;
    private final CompanyRepository companyRepository;
    private final LlmClientFactory llmClientFactory;

    /** 새벽 03:30 일 1회. (ArticleRetention 03:00 직후, 부하 분산용으로 30분 띄움.) */
    @Scheduled(cron = "0 30 3 * * *")
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        try {
            compressRawToDaily(now);
        } catch (Exception e) {
            log.error("시스템 로그 raw→DAY 압축 실패", e);
        }
        try {
            compressDailyToMonthly(now);
        } catch (Exception e) {
            log.error("시스템 로그 DAY→MONTH 압축 실패", e);
        }
        try {
            compressMonthlyToQuarterly(now);
        } catch (Exception e) {
            log.error("시스템 로그 MONTH→QUARTER 압축 실패", e);
        }
        try {
            purgeExpiredQuarters(now);
        } catch (Exception e) {
            log.error("시스템 로그 QUARTER purge 실패", e);
        }
    }

    // ─── 1단계: raw → DAY ─────────────────────────────────────────

    private void compressRawToDaily(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(RAW_RETENTION_DAYS);
        List<Long> companyIds = logRepository.findCompanyIdsWithLogsBefore(cutoff);
        for (Long companyId : companyIds) {
            try {
                summarizeRawForCompany(companyId, cutoff);
            } catch (Exception e) {
                log.warn("회사 {} raw→DAY 요약 실패", companyId, e);
            }
        }
    }

    @Transactional
    public void summarizeRawForCompany(Long companyId, LocalDateTime cutoff) {
        List<SystemLog> rows = logRepository
                .findByCompany_CompanyIdAndCreatedAtLessThanOrderByCreatedAtAsc(companyId, cutoff);
        if (rows.isEmpty()) return;
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) return;

        // 일자별 그룹핑 (LinkedHashMap 으로 시간순 유지).
        Map<LocalDate, List<SystemLog>> byDate = new LinkedHashMap<>();
        for (SystemLog r : rows) {
            byDate.computeIfAbsent(r.getCreatedAt().toLocalDate(), k -> new ArrayList<>()).add(r);
        }

        for (var entry : byDate.entrySet()) {
            LocalDate day = entry.getKey();
            List<SystemLog> group = entry.getValue();
            String summary = summarizeWithLlm(buildRawPrompt(company.getCompanyName(), day, group),
                fallbackRawSummary(day, group));
            SystemLogSummary saved = SystemLogSummary.builder()
                    .company(company)
                    .periodType(SystemLogPeriodType.DAY)
                    .periodStart(day.atStartOfDay())
                    .periodEnd(day.plusDays(1).atStartOfDay())
                    .summary(summary)
                    .rawCount(group.size())
                    .createdAt(LocalDateTime.now())
                    .build();
            summaryRepository.save(saved);
        }
        // 요약 저장 완료 후 raw 일괄 삭제. 같은 트랜잭션 안에서 처리.
        logRepository.deleteByCompanyAndCreatedAtBefore(companyId, cutoff);
    }

    // ─── 2단계: DAY → MONTH ────────────────────────────────────────

    private void compressDailyToMonthly(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(DAY_RETENTION_DAYS);
        List<Long> companyIds = summaryRepository.findCompanyIdsWithSummariesBefore(SystemLogPeriodType.DAY, cutoff);
        for (Long companyId : companyIds) {
            try {
                compressDailyForCompany(companyId, cutoff);
            } catch (Exception e) {
                log.warn("회사 {} DAY→MONTH 압축 실패", companyId, e);
            }
        }
    }

    @Transactional
    public void compressDailyForCompany(Long companyId, LocalDateTime cutoff) {
        List<SystemLogSummary> dailies = summaryRepository
                .findByCompany_CompanyIdAndPeriodTypeAndPeriodEndLessThanOrderByPeriodStartAsc(
                        companyId, SystemLogPeriodType.DAY, cutoff);
        if (dailies.isEmpty()) return;
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) return;

        Map<YearMonth, List<SystemLogSummary>> byMonth = new TreeMap<>();
        for (SystemLogSummary s : dailies) {
            YearMonth ym = YearMonth.from(s.getPeriodStart());
            byMonth.computeIfAbsent(ym, k -> new ArrayList<>()).add(s);
        }

        for (var entry : byMonth.entrySet()) {
            YearMonth ym = entry.getKey();
            List<SystemLogSummary> group = entry.getValue();
            long totalRaw = group.stream().mapToLong(SystemLogSummary::getRawCount).sum();
            String summary = summarizeWithLlm(buildSummaryPrompt(company.getCompanyName(), ym.toString(), group),
                fallbackSummary("월", ym.toString(), group, totalRaw));
            SystemLogSummary saved = SystemLogSummary.builder()
                    .company(company)
                    .periodType(SystemLogPeriodType.MONTH)
                    .periodStart(ym.atDay(1).atStartOfDay())
                    .periodEnd(ym.plusMonths(1).atDay(1).atStartOfDay())
                    .summary(summary)
                    .rawCount(totalRaw)
                    .createdAt(LocalDateTime.now())
                    .build();
            summaryRepository.save(saved);
        }
        summaryRepository.deleteByCompanyAndTypeAndPeriodEndBefore(
                companyId, SystemLogPeriodType.DAY, cutoff);
    }

    // ─── 3단계: MONTH → QUARTER ────────────────────────────────────

    private void compressMonthlyToQuarterly(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(MONTH_RETENTION_DAYS);
        List<Long> companyIds = summaryRepository.findCompanyIdsWithSummariesBefore(SystemLogPeriodType.MONTH, cutoff);
        for (Long companyId : companyIds) {
            try {
                compressMonthlyForCompany(companyId, cutoff);
            } catch (Exception e) {
                log.warn("회사 {} MONTH→QUARTER 압축 실패", companyId, e);
            }
        }
    }

    @Transactional
    public void compressMonthlyForCompany(Long companyId, LocalDateTime cutoff) {
        List<SystemLogSummary> monthlies = summaryRepository
                .findByCompany_CompanyIdAndPeriodTypeAndPeriodEndLessThanOrderByPeriodStartAsc(
                        companyId, SystemLogPeriodType.MONTH, cutoff);
        if (monthlies.isEmpty()) return;
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) return;

        // 분기 키: "yyyy-Q{1..4}" 형식. period_start 의 month 로 분기 계산.
        Map<String, List<SystemLogSummary>> byQuarter = new TreeMap<>();
        for (SystemLogSummary s : monthlies) {
            int year = s.getPeriodStart().getYear();
            int month = s.getPeriodStart().getMonthValue();
            int q = (month - 1) / 3 + 1;
            byQuarter.computeIfAbsent(year + "-Q" + q, k -> new ArrayList<>()).add(s);
        }

        for (var entry : byQuarter.entrySet()) {
            String label = entry.getKey();
            List<SystemLogSummary> group = entry.getValue();
            long totalRaw = group.stream().mapToLong(SystemLogSummary::getRawCount).sum();
            int year = Integer.parseInt(label.substring(0, 4));
            int q = Integer.parseInt(label.substring(label.length() - 1));
            LocalDateTime start = LocalDate.of(year, (q - 1) * 3 + 1, 1).atStartOfDay();
            LocalDateTime end = start.plusMonths(3);
            String summary = summarizeWithLlm(buildSummaryPrompt(company.getCompanyName(), label, group),
                fallbackSummary("분기", label, group, totalRaw));
            SystemLogSummary saved = SystemLogSummary.builder()
                    .company(company)
                    .periodType(SystemLogPeriodType.QUARTER)
                    .periodStart(start)
                    .periodEnd(end)
                    .summary(summary)
                    .rawCount(totalRaw)
                    .createdAt(LocalDateTime.now())
                    .build();
            summaryRepository.save(saved);
        }
        summaryRepository.deleteByCompanyAndTypeAndPeriodEndBefore(
                companyId, SystemLogPeriodType.MONTH, cutoff);
    }

    // ─── 4단계: QUARTER 보관 종료 ──────────────────────────────────

    @Transactional
    public void purgeExpiredQuarters(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(QUARTER_RETENTION_DAYS);
        List<Long> companyIds = summaryRepository.findCompanyIdsWithSummariesBefore(SystemLogPeriodType.QUARTER, cutoff);
        for (Long companyId : companyIds) {
            summaryRepository.deleteByCompanyAndTypeAndPeriodEndBefore(
                    companyId, SystemLogPeriodType.QUARTER, cutoff);
        }
    }

    // ─── LLM 호출 + 폴백 ──────────────────────────────────────────

    /** LLM 호출. 실패하면 폴백 텍스트 사용 — 스케줄러는 절대 죽지 않게. */
    private String summarizeWithLlm(String prompt, String fallback) {
        try {
            LlmResponse resp = llmClientFactory.generate(List.of(
                LlmMessage.system("당신은 회사 시스템 관리 로그를 한국어 5~10줄로 요약하는 보조자입니다. "
                    + "행위자·도메인별로 묶어 자연어 문장으로. 추측·창작 금지. 숫자(건수)는 인용. 마크다운·이모지 금지."),
                LlmMessage.user(prompt)
            ));
            if (resp != null && resp.content() != null && !resp.content().isBlank()) {
                return resp.content().trim();
            }
        } catch (Exception e) {
            log.warn("시스템 로그 LLM 요약 실패 — 폴백 사용. msg={}", e.getMessage());
        }
        return fallback;
    }

    private String buildRawPrompt(String companyName, LocalDate day, List<SystemLog> group) {
        StringBuilder sb = new StringBuilder();
        sb.append("회사: ").append(companyName).append("\n");
        sb.append("날짜: ").append(day.format(D)).append("\n");
        sb.append("총 행위 수: ").append(group.size()).append("\n\n");
        sb.append("로그(시각 / 행위자 / 액션 / 대상 / 상세):\n");
        int n = Math.min(group.size(), MAX_ROWS_PER_PROMPT);
        for (int i = 0; i < n; i++) {
            SystemLog r = group.get(i);
            sb.append("- ")
              .append(r.getCreatedAt() != null ? r.getCreatedAt().format(T) : "")
              .append(" / ").append(safe(r.getActorName()))
              .append(" / ").append(r.getAction() != null ? r.getAction().getLabel() : "")
              .append(" / ").append(safe(r.getTargetType())).append(":").append(safe(r.getTargetLabel()))
              .append(" / ").append(safe(r.getDetail()))
              .append("\n");
        }
        if (group.size() > MAX_ROWS_PER_PROMPT) {
            sb.append("(이하 ").append(group.size() - MAX_ROWS_PER_PROMPT).append("건 생략)\n");
        }
        sb.append("\n위 로그를 한국어 5~10줄로 요약해 주세요.");
        return sb.toString();
    }

    private String buildSummaryPrompt(String companyName, String periodLabel, List<SystemLogSummary> group) {
        StringBuilder sb = new StringBuilder();
        sb.append("회사: ").append(companyName).append("\n");
        sb.append("기간: ").append(periodLabel).append("\n");
        sb.append("하위 요약 수: ").append(group.size()).append("\n");
        sb.append("원본 행위 합계: ").append(group.stream().mapToLong(SystemLogSummary::getRawCount).sum()).append("\n\n");
        sb.append("하위 요약들:\n");
        for (SystemLogSummary s : group) {
            sb.append("- [")
              .append(s.getPeriodStart().toLocalDate().format(D))
              .append(", ").append(s.getRawCount()).append("건]\n");
            sb.append(s.getSummary() != null ? s.getSummary() : "").append("\n\n");
        }
        sb.append("위 하위 요약들을 한국어 5~10줄로 더 큰 단위로 다시 요약해 주세요.");
        return sb.toString();
    }

    private String fallbackRawSummary(LocalDate day, List<SystemLog> group) {
        return day.format(D) + " — 관리 행위 " + group.size() + "건 (AI 요약 실패, 폴백)";
    }

    private String fallbackSummary(String unit, String label, List<SystemLogSummary> group, long totalRaw) {
        return label + " " + unit + " — 하위 요약 " + group.size() + "건, 원본 " + totalRaw + "건 (AI 요약 실패, 폴백)";
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
