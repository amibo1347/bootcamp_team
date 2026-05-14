package com.team.intranet.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;

import com.team.intranet.entity.VacationRequest;
import com.team.intranet.enums.VacationType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VacationRequestDto {

    private Long vacationRequestId;
    private Long approvalId;
    private VacationType vacationType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double totalDays;
    private String reason;

    public static VacationRequestDto from(VacationRequest vr) {
        VacationRequestDto dto = new VacationRequestDto();
        dto.setVacationRequestId(vr.getVacationRequestId());
        dto.setApprovalId(vr.getApproval().getApprovalId());
        dto.setVacationType(vr.getVacationType());
        dto.setStartDate(vr.getStartDate());
        dto.setEndDate(vr.getEndDate());
        dto.setTotalDays(vr.getTotalDays());
        dto.setReason(vr.getReason());
        return dto;
    }
}
