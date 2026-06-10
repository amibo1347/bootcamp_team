package com.team.intranet.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.intranet.dto.approval.ApprovalDetailResponse;
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
import com.team.intranet.entity.ApprovalAttachment;
import com.team.intranet.entity.ApprovalFieldValue;
import com.team.intranet.entity.ApprovalLine;
import com.team.intranet.entity.Calendar;
import com.team.intranet.entity.ExpenseRequest;
import com.team.intranet.entity.FormTemplate;
import com.team.intranet.entity.GenericRequest;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.VacationRequest;
import com.team.intranet.enums.ApprovalStatus;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.HalfDayPeriod;
import com.team.intranet.enums.VacationType;
import com.team.intranet.enums.Visibility;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.Status;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.ApprovalAttachmentRepository;
import com.team.intranet.repository.ApprovalFieldValueRepository;
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
    private final ApprovalFieldValueRepository fieldValueRepository;
    private final ApprovalAttachmentRepository approvalAttachmentRepository;
    private final ObjectMapper objectMapper;

    // ===== Submit =====

    @Transactional
    public ApprovalSubmitResponse submit(MemberSession ms, ApprovalSubmitRequest req) {
        List<Long> line = validateSubmitRequest(req, ms);

        FormTemplate template = loadTemplate(req.getFormTemplateId(), ms);
        Member drafter = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        List<Member> approvers = resolveApprovers(line, ms);
        validateApprovalLineRoles(drafter.getRole(), approvers);

        Approval approval = approvalRepository.save(Approval.builder()
            .formTemplate(template)
            .drafter(drafter)
            .approver(approvers.get(0))           // 1단계 결재자 캐시
            .currentLevel(1)
            .maxLevel(approvers.size())
            .title(req.getTitle())
            .status(ApprovalStatus.PENDING)
            .draftedAt(LocalDateTime.now())
            .company(drafter.getCompany())
            .build());

        for (int i = 0; i < approvers.size(); i++) {
            approvalLineRepository.save(ApprovalLine.builder()
                .approval(approval)
                .level(i + 1)
                .approver(approvers.get(i))
                .status(ApprovalStatus.PENDING)
                .build());
        }

        saveBody(approval, template, req);
        linkAttachments(approval, req.getAttachmentIds(), drafter);
        notifyFirstApprover(approval, drafter, template, approvers.get(0));

        return new ApprovalSubmitResponse(true, approval.getApprovalId(), "제출되었습니다.");
    }

    /** submit 요청의 헤더/결재선 검증. 정규화된 결재선을 반환. */
    private List<Long> validateSubmitRequest(ApprovalSubmitRequest req, MemberSession ms) {
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
        return line;
    }

    /** 양식 조회 + 활성/회사 스코프 검증. */
    private FormTemplate loadTemplate(Long formTemplateId, MemberSession ms) {
        FormTemplate template = formTemplateRepository.findById(formTemplateId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORM_TEMPLATE_NOT_FOUND));
        if (!template.isActive()) {
            throw new BusinessException(ErrorCode.FORM_TEMPLATE_INACTIVE);
        }
        if (template.getCompany() != null
            && !template.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return template;
    }

    /** 결재자 ID 목록을 Member 로 변환하면서 같은 회사인지 검증. */
    private List<Member> resolveApprovers(List<Long> line, MemberSession ms) {
        List<Member> approvers = new ArrayList<>(line.size());
        for (Long id : line) {
            Member m = memberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            if (m.getCompany() == null
                || !m.getCompany().getCompanyId().equals(ms.getCompanyId())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            approvers.add(m);
        }
        return approvers;
    }

    /**
     * 결재 본문 저장 분기:
     *  - 회사 사본 + fieldSchema 있는 양식 = B안 동적 경로 (스냅샷 캡처 + 필드값 저장)
     *  - 그 외 = 시스템 디폴트 fixed 본문 분기
     */
    private void saveBody(Approval approval, FormTemplate template, ApprovalSubmitRequest req) {
        if (isDynamicTemplate(template)) {
            saveDynamicBody(approval, template, req.getDynamicFields());
            return;
        }
        String code = template.getFormCode() == null ? "" : template.getFormCode().toUpperCase();
        switch (code) {
            case "VACATION" -> saveVacationBody(approval, req.getVacation());
            case "GENERIC"  -> saveGenericBody(approval, req.getGeneric());
            case "EXPENSE"  -> saveExpenseBody(approval, req.getExpense());
            default -> { /* 본문 없는 양식이면 헤더만 저장 */ }
        }
    }

    /**
     * 1단계 결재자에게 알림 발송. 알림 실패가 결재 저장에 영향 없도록 흡수.
     * (REQUIRES_NEW 의 commit 시점 UnexpectedRollbackException 은 메서드 내부 try 로는 못 잡음)
     */
    private void notifyFirstApprover(Approval approval, Member drafter, FormTemplate template, Member firstApprover) {
        try {
            alertService.sendApprovalRequestAlert(
                firstApprover.getMemberId(),
                drafter.getMemberId(),
                template.getName(),
                approval.getTitle()
            );
        } catch (Exception e) {
            log.warn("APPROVAL_REQUEST 알림 발송 실패 (approvalId={}): {}",
                approval.getApprovalId(), e.getMessage());
        }
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
            case "APPROVE" -> handleApprove(approval, currentLine, req, ms, now);
            case "REJECT"  -> handleReject(approval, currentLine, req, ms, now);
            case "HOLD"    -> handleHold(approval, currentLine, req, ms, now);
            default -> throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        return new ApprovalProcessResponse(true, approval.getApprovalId(), approval.getStatus());
    }

    /** 승인 처리 — 최종 단계면 결재 완료, 아니면 다음 단계로 이동 + 알림. */
    private void handleApprove(Approval approval, ApprovalLine currentLine,
                               ApprovalProcessRequest req, MemberSession ms, LocalDateTime now) {
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
            sendResultAlertSafe(approval, ms.getMemberId(), "승인");
            return;
        }

        int nextLevel = approval.getCurrentLevel() + 1;
        ApprovalLine nextLine = approvalLineRepository
            .findByApproval_ApprovalIdAndLevel(approval.getApprovalId(), nextLevel)
            .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));
        approval.setCurrentLevel(nextLevel);
        approval.setApprover(nextLine.getApprover());
        approval.setStatus(ApprovalStatus.IN_PROGRESS);

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

    /** 반려 처리 — 현재 단계 이후 PENDING line 도 일괄 REJECTED 로 전파해 시각적 불일치 방지. */
    private void handleReject(Approval approval, ApprovalLine currentLine,
                              ApprovalProcessRequest req, MemberSession ms, LocalDateTime now) {
        currentLine.setStatus(ApprovalStatus.REJECTED);
        currentLine.setProcessedAt(now);
        currentLine.setComment(req.getComment());

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
        sendResultAlertSafe(approval, ms.getMemberId(), "반려");
    }

    /** 보류 처리 — line/approval 둘 다 ON_HOLD. */
    private void handleHold(Approval approval, ApprovalLine currentLine,
                            ApprovalProcessRequest req, MemberSession ms, LocalDateTime now) {
        currentLine.setStatus(ApprovalStatus.ON_HOLD);
        currentLine.setProcessedAt(now);
        currentLine.setComment(req.getComment());
        approval.setStatus(ApprovalStatus.ON_HOLD);
        approval.setApproverComment(req.getComment());
        approval.setProcessedAt(now);
        sendResultAlertSafe(approval, ms.getMemberId(), "보류");
    }

    // ===== Detail =====

    /**
     * 결재 단건 상세. 권한: 기안자 본인 또는 결재선에 포함된 결재자.
     * 양식별 본문(휴가/일반/지출)을 분기 조회하여 응답에 동봉한다.
     */
    public ApprovalDetailResponse getApprovalDetail(MemberSession ms, Long approvalId) {
        if (approvalId == null) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_FOUND);
        }
        Approval approval = approvalRepository.findById(approvalId)
            .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        List<ApprovalLine> lines = approvalLineRepository
            .findByApproval_ApprovalIdOrderByLevelAsc(approvalId);

        Long me = ms.getMemberId();
        boolean isDrafter = approval.getDrafter() != null
            && me.equals(approval.getDrafter().getMemberId());
        boolean isApproverOnLine = lines.stream()
            .anyMatch(l -> l.getApprover() != null && me.equals(l.getApprover().getMemberId()));
        if (!isDrafter && !isApproverOnLine) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        List<ApprovalAttachment> attachments = approvalAttachmentRepository
            .findByApproval_ApprovalId(approvalId);

        // 동적 본문 결재(B안): schemaSnapshot 이 있으면 양식 후속 변경과 무관하게 그 스냅샷 + 필드값으로 응답.
        if (approval.getSchemaSnapshot() != null && !approval.getSchemaSnapshot().isBlank()) {
            List<ApprovalFieldValue> values = fieldValueRepository
                .findByApproval_ApprovalIdOrderByFieldValueIdAsc(approvalId);
            return ApprovalDetailResponse.ofDynamic(approval, lines, values, attachments);
        }

        String code = approval.getFormTemplate().getFormCode() == null
            ? "" : approval.getFormTemplate().getFormCode().toUpperCase();

        VacationRequest vacation = "VACATION".equals(code)
            ? vacationRepository.findByApproval_ApprovalId(approvalId).orElse(null)
            : null;
        GenericRequest generic = "GENERIC".equals(code)
            ? genericRequestRepository.findByApproval_ApprovalId(approvalId).orElse(null)
            : null;
        ExpenseRequest expense = "EXPENSE".equals(code)
            ? expenseRequestRepository.findByApproval_ApprovalId(approvalId).orElse(null)
            : null;

        return ApprovalDetailResponse.of(approval, lines, vacation, generic, expense, attachments);
    }

    // ===== Delete (취소 / 삭제) =====

    /**
     * 결재 문서 취소 — 내 결재함의 [취소] 버튼.
     * 대기(PENDING)·보류(ON_HOLD) 상태에서만 가능. 진행·승인·반려는 취소 불가.
     *  - 기안자 본인만 가능.
     *  - 하위 데이터(결재선·본문·첨부·필드값)를 먼저 제거해 FK 제약 회피.
     *  - 휴가 승인 시 자동 등록된 캘린더 일정은 별도 엔티티라 영향 없음.
     */
    @Transactional
    public void deleteApproval(MemberSession ms, Long approvalId) {
        if (approvalId == null) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_FOUND);
        }
        Approval approval = approvalRepository.findById(approvalId)
            .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        // 기안자 본인만 취소 가능.
        if (approval.getDrafter() == null
            || !approval.getDrafter().getMemberId().equals(ms.getMemberId())) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        // 회사 격리.
        if (approval.getCompany() == null
            || !approval.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        // 대기·보류 상태만 취소 가능 — 진행·승인·반려는 불가.
        if (approval.getStatus() != ApprovalStatus.PENDING
            && approval.getStatus() != ApprovalStatus.ON_HOLD) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        // 자식 먼저 제거 (FK 제약 회피).
        List<ApprovalAttachment> attachments =
            approvalAttachmentRepository.findByApproval_ApprovalId(approvalId);
        if (!attachments.isEmpty()) approvalAttachmentRepository.deleteAll(attachments);

        List<ApprovalFieldValue> fieldValues =
            fieldValueRepository.findByApproval_ApprovalIdOrderByFieldValueIdAsc(approvalId);
        if (!fieldValues.isEmpty()) fieldValueRepository.deleteAll(fieldValues);

        vacationRepository.findByApproval_ApprovalId(approvalId)
            .ifPresent(vacationRepository::delete);
        genericRequestRepository.findByApproval_ApprovalId(approvalId)
            .ifPresent(genericRequestRepository::delete);
        expenseRequestRepository.findByApproval_ApprovalId(approvalId)
            .ifPresent(expenseRequestRepository::delete);

        List<ApprovalLine> lines =
            approvalLineRepository.findByApproval_ApprovalIdOrderByLevelAsc(approvalId);
        if (!lines.isEmpty()) approvalLineRepository.deleteAll(lines);

        approvalRepository.delete(approval);
    }

    // ===== Lists =====

    public ApprovalPageResponse listMyApprovals(MemberSession ms, String status, Integer page) {
        Long memberId = ms.getMemberId();
        int p = (page == null || page < 1) ? 1 : page;

        // DB 레벨 페이징 — schema_snapshot(CLOB) EAGER 컬럼을 한 페이지 분량만 fetch.
        // (이전 메모리 페이징: 모든 row + 모든 CLOB 전송 → 결재 누적 시 3~5초 지연)
        PageRequest pageable = PageRequest.of(p - 1, DEFAULT_PAGE_SIZE);
        Page<Approval> pageResult;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            pageResult = approvalRepository.findByDrafter_MemberIdOrderByDraftedAtDesc(memberId, pageable);
        } else {
            ApprovalStatus parsed = parseStatus(status);
            pageResult = (parsed == null)
                ? Page.empty(pageable)
                : approvalRepository.findByDrafter_MemberIdAndStatusOrderByDraftedAtDesc(memberId, parsed, pageable);
        }

        List<ApprovalRow> items = toRowsWithLines(pageResult.getContent());
        long total = pageResult.getTotalElements();
        int totalPages = Math.max(1, pageResult.getTotalPages());
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

    /**
     * 미리 업로드된 첨부파일을 결재 문서에 연결한다. (게시글 첨부 연결과 동일한 흐름)
     *  - 같은 회사 + 업로더가 기안자 본인인 첨부만 연결.
     *  - 이미 다른 결재에 연결된 첨부는 건너뜀.
     *  - 첨부는 선택 사항 — 목록이 비어 있으면 아무것도 안 함.
     */
    private void linkAttachments(Approval approval, List<Long> attachmentIds, Member drafter) {
        if (attachmentIds == null || attachmentIds.isEmpty()) return;
        for (Long id : attachmentIds) {
            if (id == null) continue;
            ApprovalAttachment att = approvalAttachmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
            if (att.getCompany() == null
                || !att.getCompany().getCompanyId().equals(drafter.getCompany().getCompanyId())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            if (att.getUploader() == null
                || !att.getUploader().getMemberId().equals(drafter.getMemberId())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            if (att.getApproval() != null) continue; // 이미 다른 결재에 연결됨
            att.setApproval(approval);
        }
    }

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
     * 결재 결과(승인/반려/보류) 알림 발송. 알림 실패가 결재 트랜잭션을 깨지 않도록 try 로 흡수.
     * REQUIRES_NEW 의 commit 시 발생할 수 있는 예외도 함께 잡는다.
     */
    private void sendResultAlertSafe(Approval approval, Long processorMemberId, String resultLabel) {
        try {
            alertService.sendApprovalResultAlert(
                approval.getDrafter().getMemberId(),
                processorMemberId,
                approval.getFormTemplate().getName(),
                approval.getTitle(),
                resultLabel
            );
        } catch (Exception e) {
            log.warn("APPROVAL_RESULT 알림 발송 실패 (approvalId={}, result={}): {}",
                approval.getApprovalId(), resultLabel, e.getMessage());
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
        HalfDayPeriod half = vacation.getHalfDayPeriod();
        String title = "[휴가] " + typeLabel
            + (half != null ? " (" + half.getDescription() + ")" : "");

        // 반차는 반나절 시간대로(오전 09~13, 오후 14~18), 종일 휴가는 하루 종일 일정으로 등록.
        LocalDateTime startAt;
        LocalDateTime endAt;
        boolean allDay;
        if (half == HalfDayPeriod.AM) {
            startAt = vacation.getStartDate().atTime(9, 0);
            endAt   = vacation.getStartDate().atTime(13, 0);
            allDay  = false;
        } else if (half == HalfDayPeriod.PM) {
            startAt = vacation.getStartDate().atTime(14, 0);
            endAt   = vacation.getStartDate().atTime(18, 0);
            allDay  = false;
        } else {
            startAt = vacation.getStartDate().atStartOfDay();
            endAt   = vacation.getEndDate().atTime(23, 59, 59);
            allDay  = true;
        }

        Calendar event = Calendar.builder()
            .member(approval.getDrafter())
            .title(title)
            .description(approval.getTitle())
            .startAt(startAt)
            .endAt(endAt)
            .allDay(allDay)
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
        if (payload.getStartDate() == null) {
            throw new BusinessException(ErrorCode.INVALID_VACATION_PERIOD);
        }

        HalfDayPeriod half = HalfDayPeriod.parse(payload.getHalfDayPeriod());

        LocalDate startDate = payload.getStartDate();
        LocalDate endDate;
        double totalDays;

        if (half != null) {
            // 반차 — 시작일 하루에만 적용, 0.5일로 강제. 종료일·일수 입력은 무시한다.
            endDate = startDate;
            totalDays = 0.5;
        } else {
            endDate = payload.getEndDate();
            if (endDate == null || startDate.isAfter(endDate)) {
                throw new BusinessException(ErrorCode.INVALID_VACATION_PERIOD);
            }
            totalDays = payload.getDays() == null ? 1.0 : payload.getDays();
        }

        VacationRequest v = VacationRequest.builder()
            .approval(approval)
            .vacationType(resolveVacationType(payload.getVacationType()))
            .startDate(startDate)
            .endDate(endDate)
            .totalDays(totalDays)
            .halfDayPeriod(half)
            .reason(payload.getVacationType())
            .build();
        vacationRepository.save(v);
    }

    /** B안: 회사 사본 + fieldSchema 있는 양식이면 동적 경로 사용. */
    private boolean isDynamicTemplate(FormTemplate t) {
        return t.getCompany() != null
            && t.getFieldSchema() != null
            && !t.getFieldSchema().isBlank();
    }

    /**
     * B안 동적 본문 저장:
     *  1. 양식의 fieldSchema 를 결재 문서에 스냅샷으로 박아둠 (양식 수정·삭제와 무관하게 원형 유지)
     *  2. fieldSchema 의 각 필드에 대해 required 검증 후 ApprovalFieldValue 행으로 저장.
     *     multi-select 처럼 값이 여러 개면 같은 field_key 로 여러 row.
     */
    private void saveDynamicBody(Approval approval, FormTemplate template,
                                 Map<String, List<String>> dynamicFields) {
        approval.setSchemaSnapshot(template.getFieldSchema());

        List<Map<String, Object>> schema;
        try {
            schema = objectMapper.readValue(
                template.getFieldSchema(),
                new TypeReference<List<Map<String, Object>>>() {}
            );
        } catch (Exception e) {
            log.warn("fieldSchema 파싱 실패 (templateId={}): {}",
                template.getFormTemplateId(), e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        Map<String, List<String>> values = dynamicFields == null ? Map.of() : dynamicFields;

        for (Map<String, Object> field : schema) {
            Object keyObj = field.get("key");
            if (keyObj == null) continue;
            String key = String.valueOf(keyObj);

            Object requiredObj = field.get("required");
            boolean required = requiredObj instanceof Boolean && (Boolean) requiredObj;

            List<String> vals = values.getOrDefault(key, List.of()).stream()
                .filter(v -> v != null && !v.isBlank())
                .toList();

            if (required && vals.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_STATUS);
            }

            for (String v : vals) {
                fieldValueRepository.save(ApprovalFieldValue.builder()
                    .approval(approval)
                    .fieldKey(key)
                    .fieldValue(v)
                    .build());
            }
        }
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
