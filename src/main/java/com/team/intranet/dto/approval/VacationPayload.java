package com.team.intranet.dto.approval;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 휴가 양식 본문 요청 (프론트 vacation-form.js 직렬화 형식).
 * vacationType 은 자유 텍스트로 들어오므로 enum 매칭은 서비스에서 fuzzy 로 처리.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VacationPayload {
    private String vacationType;   // 자유 텍스트 (예: "연차")
    private Double days;           // 0.5 단위
    private LocalDate startDate;
    private LocalDate endDate;
}
