package com.team.intranet.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.dto.approval.ApprovalListResponse;
import com.team.intranet.dto.approval.ApprovalPageResponse;
import com.team.intranet.dto.approval.ApprovalProcessRequest;
import com.team.intranet.dto.approval.ApprovalProcessResponse;
import com.team.intranet.dto.approval.ApprovalRow;
import com.team.intranet.dto.approval.ApprovalSubmitRequest;
import com.team.intranet.dto.approval.ApprovalSubmitResponse;
import com.team.intranet.dto.approval.ApproverCandidate;
import com.team.intranet.dto.approval.ExpensePayload;
import com.team.intranet.dto.approval.GenericPayload;
import com.team.intranet.dto.approval.VacationPayload;
import com.team.intranet.entity.Approval;
import com.team.intranet.entity.ApprovalLine;
import com.team.intranet.entity.Calendar;
import com.team.intranet.entity.ExpenseRequest;
import com.team.intranet.entity.FormTemplate;
import com.team.intranet.entity.GenericRequest;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.VacationRequest;
import com.team.intranet.enums.ApprovalStatus;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.VacationType;
import com.team.intranet.enums.Visibility;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.Status;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.ApprovalLineRepository;
import com.team.intranet.repository.ApprovalRepository;
import com.team.intranet.repository.CalendarRepository;
import com.team.intranet.repository.ExpenseRequestRepository;
import com.team.intranet.repository.FormTemplateRepository;
import com.team.intranet.repository.GenericRequestRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.repository.VacationRepository;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_APPROVAL_LEVELS = 4;

    private final ApprovalRepository approvalRepository;
    private final ApprovalLineRepository approvalLineRepository;
    private final VacationRepository vacationRepository;
    private final GenericRequestRepository genericRequestRepository;
    private final ExpenseRequestRepository expenseRequestRepository;
    private final FormTemplateRepository formTemplateRepository;
    private final MemberRepository memberRepository;
    private final CalendarRepository calendarRepository;
    private final AlertService alertService;

    // ===== Submit =====

    @Transactional
    public ApprovalSubmitResponse submit(MemberSession ms, ApprovalSubmitRequest req) {
        if (req.getFormTemplateId() == null) {
            throw new BusinessException(ErrorCode.FORM_TEMPLATE_NOT_FOUND);
        }
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        List<Long> line = normalizeApprovalLine(req);
        if (line.isEmpty()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        if (line.size() > MAX_APPROVAL_LEVELS) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        if (line.contains(ms.getMemberId())) {
            throw new BusinessException(ErrorCode.APPROVER_CANNOT_BE_SELF);
        }
        if (new HashSet<>(line).size() != line.size()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        FormTemplate template = formTemplateRepository.findById(req.getFormTemplateId())
            .orElseThrow(() -> new BusinessException(ErrorCode.FORM_TEMPLATE_NOT_FOUND));
        if (!template.isActive()) {
            throw new BusinessException(ErrorCode.FORM_TEMPLATE_INACTIVE);
        }
        if (template.getCompany() != null
            && !template.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        Member drafter = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        List<Member> approvers = new ArrayList<>();
        for (Long id : line) {
            Member m = memberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            if (m.getCompany() == null
                || !m.getCompany().getCompanyId().equals(ms.getCompanyId())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            approvers.add(m);
        }

        validateApprovalLineRoles(drafter.getRole(), approvers);

        Approval approval = Approval.builder()
            .formTemplate(template)
            .drafter(drafter)
            .approver(approvers.get(0))           // 1단계 결재자 캐시
            .currentLevel(1)
            .maxLevel(approvers.size())
            .title(req.getTitle())
            .status(ApprovalStatus.PENDING)
            .draftedAt(LocalDateTime.now())
            .company(drafter.getCompany())
            .build();
        approval = approvalRepository.save(approval);

        for (int i = 0; i < approvers.size(); i++) {
            approvalLineRepository.save(ApprovalLine.builder()
                .approval(approval)
                .level(i + 1)
                .approver(approvers.get(i))
                .status(ApprovalStatus.PENDING)
                .build());
        }

        String code = template.getFormCode() == null ? "" : template.getFormCode().toUpperCase();
        switch (code) {
            case "VACATION" -> saveVacationBody(approval, req.getVacation());
            case "GENERIC"  -> saveGenericBody(approval, req.getGeneric());
            case "EXPENSE"  -> saveExpenseBody(approval, req.getExpense());
            default -> { /* 본문 없는 양식이면 헤더만 저장 */ }
        }

        // 1단계 결재자에게 알림 발송. 알림이 실패해도 결재 저장은 영향 없도록 호출 측에서도 흡수.
        // (REQUIRES_NEW 의 commit 시점 UnexpectedRollbackException 은 메서드 내부 try 로는 못 잡음)
        try {
            alertService.sendApprovalRequestAlert(
                approvers.get(0).getMemberId(),
                drafter.getMemberId(),
                template.getName(),
                approval.getTitle()
            );
        } catch (Exception e) {
            log.warn("APPROVAL_REQUEST 알림 발송 실패 (approvalId={}): {}",
                approval.getApprovalId(), e.getMessage());
        }

        return new ApprovalSubmitResponse(true, approval.getApprovalId(), "제출되었습니다.");
    }

    // ===== Process =====

    @Transactional
    public ApprovalProcessResponse process(MemberSession ms, ApprovalProcessRequest req) {
        if (req.getApprovalId() == null || req.getAction() == null || req.getAction().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        Approval approval = approvalRepository.findById(req.getApprovalId())
            .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        if (!approval.getApprover().getMemberId().equals(ms.getMemberId())) {
            throw new BusinessException(ErrorCode.NOT_APPROVER);
        }
        if (approval.getStatus() == ApprovalStatus.APPROVED
            || approval.getStatus() == ApprovalStatus.REJECTED) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_PROCESSED);
        }

        ApprovalLine currentLine = approvalLineRepository
            .findByApproval_ApprovalIdAndLevel(approval.getApprovalId(), approval.getCurrentLevel())
            .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        String action = req.getAction().toUpperCase();
        LocalDateTime now = LocalDateTime.now();

        switch (action) {
            case "APPROVE" -> {
                currentLine.setStatus(ApprovalStatus.APPROVED);
                currentLine.setProcessedAt(now);
                currentLine.setComment(req.getComment());

                boolean isFinal = approval.getCurrentLevel().equals(approval.getMaxLevel());
                if (isFinal) {
                    approval.setStatus(ApprovalStatus.APPROVED);
                    approval.setApproverComment(req.getComment());
                    approval.setProcessedAt(now);
                    // 휴가 최종 승인 → 신청자 캘린더에 일정 자동 등록
                    if ("VACATION".equalsIgnoreCase(approval.getFormTemplate().getFormCode())) {
                        registerVacationOnCalendar(approval);
                    }
                } else {
                    int nextLevel = approval.getCurrentLevel() + 1;
                    ApprovalLine nextLine = approvalLineRepository
                        .findByApproval_ApprovalIdAndLevel(approval.getApprovalId(), nextLevel)
                        .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));
                    approval.setCurrentLevel(nextLevel);
                    approval.setApprover(nextLine.getApprover());
                    approval.setStatus(ApprovalStatus.IN_PROGRESS);

                    // 다음 단계 결재자에게 알림 발송 (실패해도 결재 진행에는 영향 없음)
                    try {
                        alertService.sendApprovalRequestAlert(
                            nextLine.getApprover().getMemberId(),
                            approval.getDrafter().getMemberId(),
                            approval.getFormTemplate().getName(),
                            approval.getTitle()
                        );
                    } catch (Exception e) {
                        log.warn("APPROVAL_REQUEST 알림 발송 실패 (approvalId={}, level={}): {}",
                            approval.getApprovalId(), nextLevel, e.getMessage());
                    }
                }
            }
            case "REJECT" -> {
                currentLine.setStatus(ApprovalStatus.REJECTED);
                currentLine.setProcessedAt(now);
                currentLine.setComment(req.getComment());

                // 반려 전파: 현재 단계 이후의 PENDING line 들도 REJECTED 로 일괄 변경
                // (드롭다운에 "대기" 로 남아 있는 시각적 불일치 방지)
                int currentLevelInt = approval.getCurrentLevel();
                List<ApprovalLine> allLines = approvalLineRepository
                    .findByApproval_ApprovalIdOrderByLevelAsc(approval.getApprovalId());
                for (ApprovalLine line : allLines) {
                    if (line.getLevel() != null
                        && line.getLevel() > currentLevelInt
                        && line.getStatus() == ApprovalStatus.PENDING) {
                        line.setStatus(ApprovalStatus.REJECTED);
                        line.setProcessedAt(now);
                    }
                }

                approval.setStatus(ApprovalStatus.REJECTED);
                approval.setApproverComment(req.getComment());
                approval.setProcessedAt(now);
            }
            case "HOLD" -> {
                currentLine.setStatus(ApprovalStatus.ON_HOLD);
                currentLine.setProcessedAt(now);
                currentLine.setComment(req.getComment());
                approval.setStatus(ApprovalStatus.ON_HOLD);
                approval.setApproverComment(req.getComment());
                approval.setProcessedAt(now);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        return new ApprovalProcessResponse(true, approval.getApprovalId(), approval.getStatus());
    }

    // ===== Lists =====

    public ApprovalPageResponse listMyApprovals(MemberSession ms, String status, Integer page) {
        Long memberId = ms.getMemberId();
        int p = (page == null || page < 1) ? 1 : page;

        List<Approval> all;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            all = approvalRepository.findByDrafter_MemberIdOrderByDraftedAtDesc(memberId);
        } else {
            ApprovalStatus parsed = parseStatus(status);
            all = (parsed == null)
                ? List.of()
                : approvalRepository.findByDrafter_MemberIdAndStatusOrderByDraftedAtDesc(memberId, parsed);
        }

        long total = all.size();
        int from = Math.min((p - 1) * DEFAULT_PAGE_SIZE, all.size());
        int to = Math.min(from + DEFAULT_PAGE_SIZE, all.size());
        List<Approval> slice = all.subList(from, to);
        List<ApprovalRow> items = toRowsWithLines(slice);
        int totalPages = (int) Math.max(1, Math.ceil((double) total / DEFAULT_PAGE_SIZE));
        return new ApprovalPageResponse(items, p, DEFAULT_PAGE_SIZE, total, totalPages);
    }

    // 관리자 대기함: 현재 단계 결재자가 본인이고 아직 처리 안 한 결재
    public ApprovalListResponse listPendingForAdmin(MemberSession ms) {
        List<Approval> rows = approvalRepository
            .findByApprover_MemberIdOrderByDraftedAtDesc(ms.getMemberId())
            .stream()
            .filter(a -> a.getStatus() == ApprovalStatus.PENDING
                || a.getStatus() == ApprovalStatus.IN_PROGRESS)
            .toList();
        List<ApprovalRow> items = toRowsWithLines(rows);
        return new ApprovalListResponse(items, items.size());
    }

    // 관리자 완료함: 본인이 어느 단계든 처리한(승인/반려/보류) 결재.
    // 1차 승인 후 다음 단계로 넘어간 IN_PROGRESS 도 포함 — "내가 승인한 이후 어떻게 진행되는지" 확인 용도.
    public ApprovalListResponse listCompletedForAdmin(MemberSession ms) {
        List<Approval> rows = approvalRepository
            .findProcessedByMember(ms.getMemberId(), ApprovalStatus.PENDING);
        List<ApprovalRow> items = toRowsWithLines(rows);
        return new ApprovalListResponse(items, items.size());
    }

    /**
     * Approval 목록을 ApprovalRow 로 변환하면서 결재선(ApprovalLine) 도 한 번의 IN 쿼리로 함께 로드.
     * 결재함 행 클릭 시 단계별 결재자 드롭다운 노출에 사용된다.
     */
    private List<ApprovalRow> toRowsWithLines(List<Approval> approvals) {
        if (approvals == null || approvals.isEmpty()) return List.of();
        List<Long> ids = approvals.stream().map(Approval::getApprovalId).toList();
        Map<Long, List<ApprovalLine>> linesByApproval = approvalLineRepository
            .findByApproval_ApprovalIdInOrderByApproval_ApprovalIdAscLevelAsc(ids)
            .stream()
            .collect(Collectors.groupingBy(l -> l.getApproval().getApprovalId()));
        return approvals.stream()
            .map(a -> ApprovalRow.from(
                a,
                linesByApproval.getOrDefault(a.getApprovalId(), Collections.emptyList())
            ))
            .toList();
    }

    // ===== Approver Candidates =====

    /**
     * 결재선 후보. 신청자 role 에 따라 필터링.
     * - USER 신청      → SUB_ADMIN 만 노출 (ADMIN/MASTER 숨김)
     * - SUB_ADMIN 신청 → SUB_ADMIN, ADMIN, MASTER 노출
     * - ADMIN/MASTER 신청 → 위 + 자신 제외
     * 항상 본인 제외, 활성(JOIN) 만.
     */
    public List<ApproverCandidate> listApproverCandidates(MemberSession ms) {
        Role drafterRole = ms.getRole();
        Set<Role> allowed = candidateRoles(drafterRole);

        return memberRepository.findByCompanyCompanyId(ms.getCompanyId()).stream()
            .filter(m -> m.getStatus() == Status.JOIN)
            .filter(m -> allowed.contains(m.getRole()))
            .filter(m -> !m.getMemberId().equals(ms.getMemberId()))
            .map(ApproverCandidate::from)
            .toList();
    }

    private Set<Role> candidateRoles(Role drafterRole) {
        if (drafterRole == Role.USER) {
            return Set.of(Role.SUB_ADMIN);
        }
        // SUB_ADMIN / ADMIN / MASTER 신청: SUB_ADMIN 이상 전체
        return Set.of(Role.SUB_ADMIN, Role.ADMIN, Role.MASTER);
    }

    // ===== 내부 헬퍼 =====

    private List<Long> normalizeApprovalLine(ApprovalSubmitRequest req) {
        if (req.getApprovalLine() != null && !req.getApprovalLine().isEmpty()) {
            return req.getApprovalLine();
        }
        if (req.getApproverMemberId() != null) {
            return List.of(req.getApproverMemberId());
        }
        return List.of();
    }

    /**
     * 결재선 role 검증.
     * - USER 신청: 결재선 전원 SUB_ADMIN. ADMIN/MASTER 포함 시 ACCESS_DENIED.
     * - SUB_ADMIN 이상 신청: 결재선 전원 SUB_ADMIN+. USER 포함 불가.
     * 최종 결재자는 어느 경우든 SUB_ADMIN+.
     */
    private void validateApprovalLineRoles(Role drafterRole, List<Member> approvers) {
        Set<Role> allowed = candidateRoles(drafterRole);
        for (Member m : approvers) {
            if (!allowed.contains(m.getRole())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
        Role finalRole = approvers.get(approvers.size() - 1).getRole();
        if (finalRole != Role.SUB_ADMIN && finalRole != Role.ADMIN && finalRole != Role.MASTER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * 휴가 최종 승인 시 신청자 캘린더에 PRIVATE 일정 자동 등록.
     * 본문(VacationRequest) 이 없으면 조용히 건너뜀.
     */
    private void registerVacationOnCalendar(Approval approval) {
        VacationRequest vacation = vacationRepository
            .findByApproval_ApprovalId(approval.getApprovalId())
            .orElse(null);
        if (vacation == null
            || vacation.getStartDate() == null
            || vacation.getEndDate() == null) {
            return;
        }
        String typeLabel = vacation.getVacationType() != null
            ? vacation.getVacationType().getDescription()
            : "휴가";
        Calendar event = Calendar.builder()
            .member(approval.getDrafter())
            .title("[휴가] " + typeLabel)
            .description(approval.getTitle())
            .startAt(vacation.getStartDate().atStartOfDay())
            .endAt(vacation.getEndDate().atTime(23, 59, 59))
            .allDay(true)
            .isRepeat(false)
            .isAlert(false)
            .visibility(Visibility.PRIVATE)
            .createdAt(LocalDateTime.now())
            .build();
        calendarRepository.save(event);
    }

    private void saveGenericBody(Approval approval, GenericPayload payload) {
        if (payload == null || payload.getContent() == null || payload.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        genericRequestRepository.save(GenericRequest.builder()
            .approval(approval)
            .content(payload.getContent())
            .build());
    }

    private void saveExpenseBody(Approval approval, ExpensePayload payload) {
        if (payload == null
            || payload.getAmount() == null || payload.getAmount() < 0
            || payload.getCategory() == null || payload.getCategory().isBlank()
            || payload.getSpentAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        expenseRequestRepository.save(ExpenseRequest.builder()
            .approval(approval)
            .amount(payload.getAmount())
            .category(payload.getCategory())
            .spentAt(payload.getSpentAt())
            .description(payload.getDescription())
            .build());
    }

    private void saveVacationBody(Approval approval, VacationPayload payload) {
        if (payload == null) {
            throw new BusinessException(ErrorCode.VACATION_REQUEST_NOT_FOUND);
        }
        if (payload.getStartDate() == null || payload.getEndDate() == null) {
            throw new BusinessException(ErrorCode.INVALID_VACATION_PERIOD);
        }
        if (payload.getStartDate().isAfter(payload.getEndDate())) {
            throw new BusinessException(ErrorCode.INVALID_VACATION_PERIOD);
        }

        VacationRequest v = VacationRequest.builder()
            .approval(approval)
            .vacationType(resolveVacationType(payload.getVacationType()))
            .startDate(payload.getStartDate())
            .endDate(payload.getEndDate())
            .totalDays(payload.getDays() == null ? 1.0 : payload.getDays())
            .reason(payload.getVacationType())
            .build();
        vacationRepository.save(v);
    }

    // VacationType 자유 텍스트 입력을 enum 으로 매핑. 정확 일치 우선, description 부분 일치 fallback, ETC 최후.
    private VacationType resolveVacationType(String input) {
        if (input == null || input.isBlank()) return VacationType.ETC;
        try {
            return VacationType.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        for (VacationType t : VacationType.values()) {
            String desc = t.getDescription();
            if (desc.equals(input) || desc.contains(input) || input.contains(desc)) {
                return t;
            }
        }
        return VacationType.ETC;
    }

    private ApprovalStatus parseStatus(String s) {
        try {
            return ApprovalStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
