package com.team.intranet.dto.approval;

import com.team.intranet.dto.FormTemplateDto;
import com.team.intranet.entity.FormTemplate;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 프론트 계약(/api/approval/form-templates) 응답 형식.
 * 필드명: id, formCode, name, isActive  (백엔드 내부의 formTemplateId 는 id 로 노출)
 */
@Getter
@AllArgsConstructor
public class FormTemplateResponse {

    private Long id;
    private String formCode;
    private String name;
    private boolean isActive;

    public static FormTemplateResponse from(FormTemplate t) {
        return new FormTemplateResponse(
            t.getFormTemplateId(),
            t.getFormCode(),
            t.getName(),
            t.isActive()
        );
    }

    public static FormTemplateResponse fromDto(FormTemplateDto d) {
        return new FormTemplateResponse(
            d.getFormTemplateId(),
            d.getFormCode(),
            d.getName(),
            d.isActive()
        );
    }
}
