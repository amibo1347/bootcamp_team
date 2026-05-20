package com.team.intranet.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MASTER 사용량 대시보드의 회사 1행.
 */
@Getter
@AllArgsConstructor
public class CompanyUsageDto {

    private final Long companyId;
    private final String companyName;
    private final boolean active;
    private final long memberCount;
    private final long articleCount;
    private final long approvalCount;
}
