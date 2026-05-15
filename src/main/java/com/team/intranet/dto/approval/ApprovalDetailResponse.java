package com.team.intranet.dto.approval;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.team.intranet.entity.Approval;
import com.team.intranet.entity.ApprovalFieldValue;
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

    // B안: 동적 본문. schemaSnapshot 은 결재 시점 양식 JSON, dynamicValues 는 field_key → 값 리스트.
    // 양식이 후에 수정·삭제돼도 결재 본문 렌더는 schemaSnapshot 으로 원형 유지.
    private String schemaSnapshot;
    private Map<String, List<String>> dynamicValues;

    public static ApprovalDetailResponse of(
        Approval a,
        List<ApprovalLine> lines,
        VacationRequest vacation,
        GenericRequest generic,
        ExpenseRequest expense
    ) {
        return baseBuilder(a, lines)
            .vacation(vacation == null ? null : VacationBody.from(vacation))
            .generic(generic == null ? null : GenericBody.from(generic))
            .expense(expense == null ? null : ExpenseBody.from(expense))
            .build();
    }

    /** B안: 동적 본문 결재 응답. fixed 본문은 모두 null. */
    public static ApprovalDetailResponse ofDynamic(
        Approval a,
        List<ApprovalLine> lines,
        List<ApprovalFieldValue> values
    ) {
        Map<String, List<String>> grouped = values == null ? Map.of()
            : values.stream().collect(Collectors.groupingBy(
                ApprovalFieldValue::getFieldKey,
                LinkedHashMap::new,
                Collectors.mapping(ApprovalFieldValue::getFieldValue, Collectors.toList())
            ));
        return baseBuilder(a, lines)
            .schemaSnapshot(a.getSchemaSnapshot())
            .dynamicValues(grouped)
            .build();
    }

    private static ApprovalDetailResponseBuilder baseBuilder(Approval a, List<ApprovalLine> lines) {
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
            .approvers(lines == null ? List.of() : lines.stream().map(ApprovalLineRow::from).toList());
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
