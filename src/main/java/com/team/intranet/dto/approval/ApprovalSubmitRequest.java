package com.team.intranet.dto.approval;

import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/approval/submit 요청.
 * 결재선: approvalLine(List<Long>) 사용 — 순서 = 단계(level 1, 2, ..., N), 마지막이 최종 결재자.
 * 단일 결재자 호환: approvalLine 이 비어 있으면 approverMemberId 단일을 1단계 결재선으로 변환.
 *
 * 본문은 양식 종류에 따라 분기:
 *  - 시스템 디폴트(VACATION/GENERIC/EXPENSE): vacation/generic/expense payload 사용
 *  - 회사 사본 + fieldSchema 있는 동적 양식 (B안): dynamicFields 사용
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
    // B안: 동적 양식 본문. key = FormTemplate.fieldSchema 의 필드 key, value = 입력값 리스트
    // (단일값도 size=1 리스트, multi-select 는 N 개). 빈 리스트/누락은 빈값으로 간주.
    private Map<String, List<String>> dynamicFields;
    // 첨부파일 — 미리 업로드한 ApprovalAttachment id 목록. 선택 사항(없으면 null/빈 리스트).
    private List<Long> attachmentIds;
}
