package com.team.intranet.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.dto.CompanyUsageDto;
import com.team.intranet.dto.MasterUsageSummaryDto;
import com.team.intranet.dto.UsageTrendPointDto;
import com.team.intranet.entity.Company;
import com.team.intranet.repository.ApprovalAttachmentRepository;
import com.team.intranet.repository.ApprovalRepository;
import com.team.intranet.repository.ArticleRepository;
import com.team.intranet.repository.AttachmentRepository;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.repository.SystemLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 사용량 대시보드 통계.
 *  - 회사별 회원·게시글·결재·스토리지·이번달 신규·마지막 활동 집계.
 *  - 단일 GROUP BY 쿼리로 회사 N개에 대해 상수 횟수의 query 만 실행 (이전 N+1 회피).
 *  - KPI / 시계열 데이터도 같은 패턴.
 *
 *  스토리지 산정 범위:
 *   - ArticleAttachment + ApprovalAttachment 의 fileSize 합.
 *   - ArticleImage 는 file_size 컬럼 미존재(BLOB length 별도 산정 필요), ChatAttachment 는 회사 FK 부재
 *     로 현재는 집계 대상에서 제외 — 별도 작업 시 합산식만 확장하면 됨.
 */
@Service
@RequiredArgsConstructor
public class MasterStatsService {

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final ArticleRepository articleRepository;
    private final ApprovalRepository approvalRepository;
    private final AttachmentRepository articleAttachmentRepository;
    private final ApprovalAttachmentRepository approvalAttachmentRepository;
    private final SystemLogRepository systemLogRepository;

    /** 전체 회사의 사용량 행 목록 — 단일 GROUP BY 쿼리들로 조합 후 매핑. */
    @Transactional(readOnly = true)
    public List<CompanyUsageDto> usageList() {
        List<Company> companies = companyRepository.findAll(Sort.by(Sort.Direction.ASC, "companyId"));

        Map<Long, Long> memberCounts          = toMap(memberRepository.countMembersPerCompany());
        Map<Long, Long> articleCounts         = toMap(articleRepository.countArticlesPerCompany());
        Map<Long, Long> approvalCounts        = toMap(approvalRepository.countApprovalsPerCompany());
        Map<Long, Long> articleAttachmentSum  = toMap(articleAttachmentRepository.sumFileSizePerCompany());
        Map<Long, Long> approvalAttachmentSum = toMap(approvalAttachmentRepository.sumFileSizePerCompany());
        Map<Long, Long> newMembersThisMonth   = toMap(memberRepository
                .countMembersPerCompanySince(startOfCurrentMonth()));
        Map<Long, LocalDateTime> lastActivity = toLastActivityMap(systemLogRepository.findLastActivityPerCompany());

        List<CompanyUsageDto> rows = new ArrayList<>(companies.size());
        for (Company company : companies) {
            Long id = company.getCompanyId();
            long storage = articleAttachmentSum.getOrDefault(id, 0L)
                         + approvalAttachmentSum.getOrDefault(id, 0L);
            rows.add(new CompanyUsageDto(
                    id,
                    company.getCompanyName(),
                    company.isActiveCompany(),
                    memberCounts.getOrDefault(id, 0L),
                    articleCounts.getOrDefault(id, 0L),
                    approvalCounts.getOrDefault(id, 0L),
                    storage,
                    newMembersThisMonth.getOrDefault(id, 0L),
                    lastActivity.get(id)));
        }
        return rows;
    }

    /** 상단 KPI 카드 — 전사 합계 + 활성/비활성 회사 수 + 이번 달 신규 회원. */
    @Transactional(readOnly = true)
    public MasterUsageSummaryDto summary() {
        long totalCompanies    = companyRepository.count();
        long activeCompanies   = companyRepository.findAll().stream()
                                                  .filter(Company::isActiveCompany).count();
        long inactiveCompanies = totalCompanies - activeCompanies;

        long totalMembers       = memberRepository.countAllMembers();
        long newMembersThisMonth = memberRepository.countAllMembersSince(startOfCurrentMonth());
        long totalArticles      = articleRepository.countAllArticles();
        long totalApprovals     = approvalRepository.countAllApprovals();
        long totalStorageBytes  = articleAttachmentRepository.sumAllFileSize()
                                + approvalAttachmentRepository.sumAllFileSize();

        return new MasterUsageSummaryDto(
                totalCompanies, activeCompanies, inactiveCompanies,
                totalMembers, newMembersThisMonth,
                totalArticles, totalApprovals, totalStorageBytes);
    }

    /**
     * 최근 N일 신규 회원·게시글 추이.
     *  - days=30 권장. since = 오늘 - (days-1) 의 00:00:00.
     *  - 누락 날짜는 0 으로 채워 연속 시계열 반환 (차트 X축 일관성).
     *  - DB 에서 그룹화하려면 EXTRACT/TRUNC dialect 분기가 필요해, KPI 규모(N일 ≪ 1년) 라서
     *    timestamp 목록만 가져와 Java 에서 toLocalDate() 그룹화 — 단순하고 이식성 높음.
     */
    @Transactional(readOnly = true)
    public List<UsageTrendPointDto> dailyTrend(int days) {
        int span = Math.max(1, days);
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(span - 1L);
        LocalDateTime since = from.atStartOfDay();

        Map<LocalDate, Long> memberDaily  = groupByDate(memberRepository.findCreatedAtSince(since));
        Map<LocalDate, Long> articleDaily = groupByDate(articleRepository.findCreatedAtSinceNotDeleted(since));

        List<UsageTrendPointDto> out = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            LocalDate d = from.plusDays(i);
            out.add(new UsageTrendPointDto(d,
                    memberDaily.getOrDefault(d, 0L),
                    articleDaily.getOrDefault(d, 0L)));
        }
        return out;
    }

    /** LocalDateTime 리스트 → LocalDate 별 카운트 Map. null timestamp 는 제외. */
    private static Map<LocalDate, Long> groupByDate(List<LocalDateTime> timestamps) {
        Map<LocalDate, Long> map = new HashMap<>();
        for (LocalDateTime t : timestamps) {
            if (t == null) continue;
            map.merge(t.toLocalDate(), 1L, Long::sum);
        }
        return map;
    }

    // ---------------------------------------------------------------------------
    // 내부 유틸
    // ---------------------------------------------------------------------------

    private static LocalDateTime startOfCurrentMonth() {
        return LocalDate.now().withDayOfMonth(1).atStartOfDay();
    }

    /** [companyId(Long), count(Long)] 형태의 Object[] 리스트를 Map 으로 변환. */
    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>(Math.max(16, rows.size() * 2));
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null) continue;
            Long key = ((Number) row[0]).longValue();
            long value = row[1] == null ? 0L : ((Number) row[1]).longValue();
            map.put(key, value);
        }
        return map;
    }

    /** [companyId, MAX(createdAt)] → Map. */
    private static Map<Long, LocalDateTime> toLastActivityMap(List<Object[]> rows) {
        Map<Long, LocalDateTime> map = new HashMap<>(Math.max(16, rows.size() * 2));
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null) continue;
            Long key = ((Number) row[0]).longValue();
            if (row[1] instanceof LocalDateTime ts) {
                map.put(key, ts);
            } else if (row[1] instanceof java.sql.Timestamp tsSql) {
                map.put(key, tsSql.toLocalDateTime());
            }
        }
        return map;
    }

}
