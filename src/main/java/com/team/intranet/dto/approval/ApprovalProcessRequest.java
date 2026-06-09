package com.team.intranet.dto.approval;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/approval/process 요청.
 * action: APPROVE | REJECT  (HOLD 는 프론트 Mock 전용 — 서버 enum 없음, 400 으로 처리)
 */
@Data
@NoArgsConstructor
public class ApprovalProcessRequest {
    private Long approvalId;
    private String action;
    private String comment;
}
