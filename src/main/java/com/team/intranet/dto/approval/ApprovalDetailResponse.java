package com.team.intranet.dto.approval;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.team.intranet.entity.Approval;
import com.team.intranet.entity.ApprovalLine;
import com.team.intranet.entity.ExpenseRequest;
import com.team.intranet.entity.GenericRequest;
import com.team.intranet.entity.VacationRequest;
import com.team.intranet.enums.ApprovalStatus;
import com.team.intranet.enums.VacationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * GET /api/approval/{id} 응답.
 * 헤더 + 결재선 + 양식별 본문(있을 때만).
 * 프론트 상세 모달이 양식별 분기로 사용.
 */
@Getter
@AllArgsConstructor
@Builder
public class ApprovalDetailResponse {

    private Long approvalId;
    private String title;
    private ApprovalStatus status;
    private Long formTemplateId;
    private String formCode;
    private String formName;
    private Long drafterMemberId;
    private String drafterName;
    private String drafterDeptName;
    private String drafterPositionName;
    private String approverComment;
    private LocalDateTime draftedAt;
    private LocalDateTime processedAt;
    private Integer currentLevel;
    private Integer maxLevel;
    private List<ApprovalLineRow> approvers;

    // 양식별 본문 (해당 없으면 null)
    private VacationBody vacation;
    private GenericBody generic;
    private ExpenseBody expense;

    public static ApprovalDetailResponse of(
        Approval a,
        List<ApprovalLine> lines,
        VacationRequest vacation,
        GenericRequest generic,
        ExpenseRequest expense
    ) {
        return ApprovalDetailResponse.builder()
            .approvalId(a.getApprovalId())
            .title(a.getTitle())
            .status(a.getStatus())
            .formTemplateId(a.getFormTemplate().getFormTemplateId())
            .formCode(a.getFormTemplate().getFormCode())
            .formName(a.getFormTemplate().getName())
            .drafterMemberId(a.getDrafter().getMemberId())
            .drafterName(a.getDrafter().getName())
            .drafterDeptName(a.getDrafter().getDept() != null ? a.getDrafter().getDept().getDeptName() : null)
            .drafterPositionName(a.getDrafter().getPosition() != null ? a.getDrafter().getPosition().getPositionName() : null)
            .approverComment(a.getApproverComment())
            .draftedAt(a.getDraftedAt())
            .processedAt(a.getProcessedAt())
            .currentLevel(a.getCurrentLevel())
            .maxLevel(a.getMaxLevel())
            .approvers(lines == null ? List.of() : lines.stream().map(ApprovalLineRow::from).toList())
            .vacation(vacation == null ? null : VacationBody.from(vacation))
            .generic(generic == null ? null : GenericBody.from(generic))
            .expense(expense == null ? null : ExpenseBody.from(expense))
            .build();
    }

    @Getter
    @AllArgsConstructor
    public static class VacationBody {
        private VacationType vacationType;
        private String vacationTypeLabel;
        private LocalDate startDate;
        private LocalDate endDate;
        private Double totalDays;
        private String reason;

        static VacationBody from(VacationRequest v) {
            return new VacationBody(
                v.getVacationType(),
                v.getVacationType() != null ? v.getVacationType().getDescription() : null,
                v.getStartDate(),
                v.getEndDate(),
                v.getTotalDays(),
                v.getReason()
            );
        }
    }

    @Getter
    @AllArgsConstructor
    public static class GenericBody {
        private String content;

        static GenericBody from(GenericRequest g) {
            return new GenericBody(g.getContent());
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ExpenseBody {
        private Long amount;
        private String category;
        private LocalDate spentAt;
        private String description;

        static ExpenseBody from(ExpenseRequest e) {
            return new ExpenseBody(e.getAmount(), e.getCategory(), e.getSpentAt(), e.getDescription());
        }
    }
}
