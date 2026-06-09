package com.team.intranet.dto.approval;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 일반 기안(GENERIC) 본문 요청.
 * 정형 필드 없이 본문 텍스트만.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenericPayload {
    private String content;
}
