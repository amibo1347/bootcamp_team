package com.team.intranet.dto.approval;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import com.team.intranet.entity.Approval;
import com.team.intranet.entity.ApprovalLine;
import com.team.intranet.enums.ApprovalStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 결재 목록/응답 항목 (프론트 approval-mock-data.js MockApprovalRow 형식과 동일).
 * LAZY 필드 접근하므로 트랜잭션 안에서 변환할 것.
 * approvers: 단계 오름차순. 마지막 요소가 최종 승인자.
 */
@Getter
@AllArgsConstructor
public class ApprovalRow {

    private Long approvalId;
    private String title;
    private ApprovalStatus status;
    private Long formTemplateId;
    private String formCode;
    private Long drafterMemberId;
    private String drafterName;
    private Long approverMemberId;
    private String approverName;
    private String approverComment;
    private LocalDateTime draftedAt;
    private LocalDateTime processedAt;
    private Integer currentLevel;
    private Integer maxLevel;
    private List<ApprovalLineRow> approvers;

    public static ApprovalRow from(Approval a) {
        return from(a, Collections.emptyList());
    }

    public static ApprovalRow from(Approval a, List<ApprovalLine> lines) {
        List<ApprovalLineRow> approverRows = lines == null
            ? Collections.emptyList()
            : lines.stream().map(ApprovalLineRow::from).toList();
        return new ApprovalRow(
            a.getApprovalId(),
            a.getTitle(),
            a.getStatus(),
            a.getFormTemplate().getFormTemplateId(),
            a.getFormTemplate().getFormCode(),
            a.getDrafter().getMemberId(),
            a.getDrafter().getName(),
            a.getApprover().getMemberId(),
            a.getApprover().getName(),
            a.getApproverComment(),
            a.getDraftedAt(),
            a.getProcessedAt(),
            a.getCurrentLevel(),
            a.getMaxLevel(),
            approverRows
        );
    }
}
