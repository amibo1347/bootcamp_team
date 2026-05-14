package com.team.intranet.dto.approval;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/approval/submit 요청.
 * 결재선: approvalLine(List<Long>) 사용 — 순서 = 단계(level 1, 2, ..., N), 마지막이 최종 결재자.
 * 단일 결재자 호환: approvalLine 이 비어 있으면 approverMemberId 단일을 1단계 결재선으로 변환.
 */
@Data
@NoArgsConstructor
public class ApprovalSubmitRequest {
    private Long formTemplateId;
    private String formCode;
    private Long approverMemberId;        // 하위 호환 (1단계 결재선)
    private List<Long> approvalLine;      // 신규: 결재선 (최대 4명)
    private String title;
    private Long drafterMemberId;         // 무시 (세션 사용)
    private VacationPayload vacation;
    private GenericPayload generic;
    private ExpensePayload expense;
}
