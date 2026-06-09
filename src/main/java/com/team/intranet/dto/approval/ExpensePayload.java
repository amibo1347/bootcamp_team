package com.team.intranet.dto.approval;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 지출결의서(EXPENSE) 본문 요청.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpensePayload {
    private Long amount;          // 원 단위
    private String category;      // 자유 텍스트 (식대/교통비/출장비 등)
    private LocalDate spentAt;
    private String description;
}
