package com.team.intranet.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;

import com.team.intranet.enums.VacationType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalCreateRequest {

    // 공통 헤더
    private String formCode;     // 어떤 양식인지 (예: "VACATION")
    private Long approverId;     // 결재자 memberId
    private String title;        // 결재 문서 제목

    // 휴가 본문 (formCode == "VACATION" 일 때 사용)
    private VacationType vacationType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double totalDays;
    private String reason;
}
