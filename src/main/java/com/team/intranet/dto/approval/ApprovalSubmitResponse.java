package com.team.intranet.dto.approval;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * POST /api/approval/submit 응답 (프론트 mockSubmitApproval 응답 형식과 동일).
 */
@Getter
@AllArgsConstructor
public class ApprovalSubmitResponse {
    private boolean ok;
    private Long approvalId;
    private String message;
}
