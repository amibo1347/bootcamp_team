package com.team.intranet.dto.approval;

import com.team.intranet.enums.ApprovalStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * POST /api/approval/process 응답 (프론트 mockProcessApproval 응답 형식과 동일).
 */
@Getter
@AllArgsConstructor
public class ApprovalProcessResponse {
    private boolean ok;
    private Long approvalId;
    private ApprovalStatus status;
}
