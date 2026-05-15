package com.team.intranet.dto.approval;

import com.team.intranet.dto.FormTemplateDto;
import com.team.intranet.entity.FormTemplate;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 프론트 계약(/api/approval/form-templates, /api/approval/admin/form-templates) 응답 형식.
 *
 * 필드:
 *  - id              : formTemplateId
 *  - formCode        : 양식 식별 코드 (VACATION/GENERIC/EXPENSE 또는 회사 정의)
 *  - name            : 표시명
 *  - content         : 양식 설명 (관리자 화면에서 사용)
 *  - isActive        : 노출 여부
 *  - isSystemDefault : true면 company_id NULL — 직접 수정 불가, fork 만 가능
 *  - fieldSchema     : B안 진입용 필드 정의 JSON. A안에선 항상 null.
 */
@Getter
@AllArgsConstructor
public class FormTemplateResponse {

    private Long id;
    private String formCode;
    private String name;
    private String content;
    private boolean isActive;
    private boolean isSystemDefault;
    private String fieldSchema;

    public static FormTemplateResponse from(FormTemplate t) {
        return new FormTemplateResponse(
            t.getFormTemplateId(),
            t.getFormCode(),
            t.getName(),
            t.getContent(),
            t.isActive(),
            t.getCompany() == null,
            t.getFieldSchema()
        );
    }

    public static FormTemplateResponse fromDto(FormTemplateDto d) {
        return new FormTemplateResponse(
            d.getFormTemplateId(),
            d.getFormCode(),
            d.getName(),
            d.getContent(),
            d.isActive(),
            false, // DTO 변환 경로는 회사 스코프에서만 호출되므로 디폴트 아님
            d.getFieldSchema()
        );
    }
}
