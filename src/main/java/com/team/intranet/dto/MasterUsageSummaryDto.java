package com.team.intranet.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MASTER 사용량 대시보드 상단 KPI 카드용 전사(全社) 집계.
 *  - 회사·회원·게시글·결재·스토리지의 시스템 전체 합계 + 이번 달 신규 회원 수.
 */
@Getter
@AllArgsConstructor
public class MasterUsageSummaryDto {

    private final long totalCompanies;
    private final long activeCompanies;
    private final long inactiveCompanies;
    private final long totalMembers;
    private final long newMembersThisMonth;
    private final long totalArticles;
    private final long totalApprovals;
    private final long totalStorageBytes;
}
