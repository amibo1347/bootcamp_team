package com.team.intranet.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

import com.team.intranet.enums.ApprovalStatus;
import com.team.intranet.entity.VacationRequest;
import com.team.intranet.entity.Approval;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalDto {
    private Long approvalId;
    private Long formTemplateId;
    private String formCode;
    private String formName;
    private Long drafterId;
    private String drafterName;
    private Long approverId;
    private String approverName;
    private String title;
    private ApprovalStatus status;
    private String approverComment;
    private LocalDateTime draftedAt;
    private LocalDateTime processedAt;
    private VacationRequestDto vacationRequest;

    // 목록용: 본문은 비워둠. LAZY 필드(formTemplate/drafter/approver) 접근하므로 트랜잭션 안에서 호출할 것.
    public static ApprovalDto from(Approval approval) {
        ApprovalDto dto = new ApprovalDto();
        dto.setApprovalId(approval.getApprovalId());
        dto.setFormTemplateId(approval.getFormTemplate().getFormTemplateId());
        dto.setFormCode(approval.getFormTemplate().getFormCode());
        dto.setFormName(approval.getFormTemplate().getName());
        dto.setDrafterId(approval.getDrafter().getMemberId());
        dto.setDrafterName(approval.getDrafter().getName());
        dto.setApproverId(approval.getApprover().getMemberId());
        dto.setApproverName(approval.getApprover().getName());
        dto.setTitle(approval.getTitle());
        dto.setStatus(approval.getStatus());
        dto.setApproverComment(approval.getApproverComment());
        dto.setDraftedAt(approval.getDraftedAt());
        dto.setProcessedAt(approval.getProcessedAt());
        return dto;
    }

    // 상세용: 본문 동봉. vacation 이 null 이면 헤더만 채워서 반환.
    public static ApprovalDto fromWithBody(Approval approval, VacationRequest vacation) {
        ApprovalDto dto = from(approval);
        if (vacation != null) {
            dto.setVacationRequest(VacationRequestDto.from(vacation));
        }
        return dto;
    }
}
