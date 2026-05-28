package com.team.intranet.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MASTER 사용량 대시보드의 회사 1행.
 *  - 스토리지: ArticleAttachment + ApprovalAttachment 의 fileSize 합 (BLOB 보유 테이블 중 file_size 컬럼이 있는 것만).
 *    ArticleImage / ChatAttachment 는 file_size 컬럼 또는 회사 FK 부재로 합산 대상에서 제외 (별도 작업).
 *  - 마지막 활동: SystemLog(관리자 액션) 의 MAX(createdAt). 일반 회원의 조회/작성은 시스템 로그 대상이 아니라
 *    완전한 "최종 사용자 활동" 지표는 아니지만, 현재 스키마에서 회사 단위로 가장 신선한 timestamp.
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
    private final long storageBytes;
    private final long newMembersThisMonth;
    private final LocalDateTime lastActivityAt;
}
