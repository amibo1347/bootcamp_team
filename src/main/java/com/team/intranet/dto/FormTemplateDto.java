package com.team.intranet.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import com.team.intranet.entity.FormTemplate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormTemplateDto {
    
    private Long formTemplateId;
    private String formCode;
    private String name;
    private String content;
    private boolean isActive;

    public static FormTemplateDto from(FormTemplate form){
        FormTemplateDto dto = new FormTemplateDto();
        dto.setFormTemplateId(form.getFormTemplateId());
        dto.setFormCode(form.getFormCode());
        dto.setName(form.getName());
        dto.setContent(form.getContent());
        dto.setActive(form.isActive());
        return dto;
    }
}
