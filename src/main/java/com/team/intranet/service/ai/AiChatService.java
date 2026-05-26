package com.team.intranet.service.ai;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.dto.ai.AiCalendarProposalDto;
import com.team.intranet.dto.ai.AiChatMessageDto;
import com.team.intranet.dto.ai.AiChatSessionDto;
import com.team.intranet.dto.ai.AiExpenseProposalDto;
import com.team.intranet.dto.ai.AiLeaveProposalDto;
import com.team.intranet.dto.ai.LlmMessage;
import com.team.intranet.dto.ai.LlmResponse;
import com.team.intranet.dto.approval.ApprovalSubmitRequest;
import com.team.intranet.dto.approval.ApproverCandidate;
import com.team.intranet.dto.approval.ExpensePayload;
import com.team.intranet.dto.approval.VacationPayload;
import com.team.intranet.entity.AiChatMessage;
import com.team.intranet.entity.AiChatSession;
import com.team.intranet.entity.AiConfig;
import com.team.intranet.entity.FormTemplate;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.AiChatRole;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.VacationType;
import com.team.intranet.enums.member.Status;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.AiChatMessageRepository;
import com.team.intranet.repository.AiChatSessionRepository;
import com.team.intranet.repository.FormTemplateRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.service.ApprovalService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * AI 비서 도메인 서비스 — 세션 관리 + LLM 호출.
 *  - 세션 = 회원 1명 소유. 다른 회원은 접근 불가.
 *  - 메시지 = USER/ASSISTANT 교대로 저장.
 *  - 호출 시 최근 N개 메시지를 LLM context 로 전달 (토큰 한계 대응).
 *  - 회원당 일일 USER 메시지 수 제한 = AiConfig.rateLimitPerDay.
 */
@Service
@RequiredArgsConstructor
public class AiChatService {

    /** LLM 호출 시 전달할 최근 메시지 수 (USER+ASSISTANT 합산). */
    private static final int HISTORY_LIMIT = 8;
    /** 세션 제목 자동 갱신 시 첫 USER 메시지에서 잘라낼 길이 — ChatGPT 등 평균(30~40자) 톤. */
    private static final int TITLE_MAX_LEN = 40;
    /** 사용자 직접 [제목 수정] 시 허용 최대 길이 (DB title 컬럼 100 길이와 정합). */
    private static final int TITLE_USER_MAX_LEN = 60;
    /** 결재자 후보가 이 수 이하면 system prompt 에 전체 명단 첨부, 초과하면 생략 (토큰 절약). */
    private static final int LEAVE_CANDIDATE_LIST_LIMIT = 30;

    /** 게시판/일정 컨텍스트를 system prompt 에 첨부할지 판단하는 키워드. */
    private static final java.util.regex.Pattern BOARD_KEYWORDS = java.util.regex.Pattern.compile(
        "게시판|공지|게시글|자유게시판|익명|복지|공지사항|작성자|글쓴이");
    private static final java.util.regex.Pattern CALENDAR_KEYWORDS = java.util.regex.Pattern.compile(
        "일정|캘린더|등록|수정|삭제|약속|미팅|회의|언제|"
        + "아침|오전|점심|오후|저녁|밤|새벽|"
        + "월요일|화요일|수요일|목요일|금요일|토요일|일요일|"
        + "내일|모레|오늘|어제|이번\\s*주|다음\\s*주|이번\\s*달|다음\\s*달|"
        + "\\d{1,2}\\s*시|\\d{1,2}\\s*일|\\d{1,2}\\s*월");
    /** 휴가 신청 키워드. "결재선/결재자" 같은 공용 단어는 제외 — 지출 대화를 휴가로 오인하지 않도록. */
    private static final java.util.regex.Pattern LEAVE_KEYWORDS = java.util.regex.Pattern.compile(
        "휴가|연차|반차|월차|병가|경조사|휴직|연가");
    /** 지출결의서 신청 — 결재선 + 직급 컨텍스트를 첨부해야 함. */
    private static final java.util.regex.Pattern EXPENSE_KEYWORDS = java.util.regex.Pattern.compile(
        "지출|결의서|지출\\s*결의|경비|영수증|정산|식대|교통비|출장비|회식비|법인카드|법카|환급|비용\\s*청구|비용\\s*처리");

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final MemberRepository memberRepository;
    private final AiConfigService aiConfigService;
    private final LlmClientFactory llmClientFactory;
    private final AiBoardContextService aiBoardContextService;
    private final AiCalendarContextService aiCalendarContextService;
    private final AiProposalExtractor proposalExtractor;
    private final com.team.intranet.service.CalendarService calendarService;
    private final com.team.intranet.repository.CalendarRepository calendarRepository;
    private final FormTemplateRepository formTemplateRepository;
    private final ApprovalService approvalService;
    // FAIL_ON_UNKNOWN_PROPERTIES=false — enrichApproverProposal 이 추가한 표시용 필드(approvers 등)가
    // DTO(record)에 없어도 역직렬화가 실패하지 않도록.
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final DateTimeFormatter HUMAN_FMT = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm");
    private static final DateTimeFormatter HUMAN_TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm");

    // ─── 세션 ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AiChatSessionDto> findMySessions(MemberSession ms) {
        Member me = loadMember(ms);
        List<AiChatSession> sessions = sessionRepository.findByMemberOrderByUpdatedAtDesc(me);
        // 정렬: 고정(pinned)이 항상 위 — 고정끼리는 제목 가나다순, 비고정끼리는 updatedAt 최신순 (Repository 기본 정렬 유지).
        // sort 는 stable 이라 같은 그룹 내에서는 기존 순서가 보존된다.
        sessions = new ArrayList<>(sessions);
        sessions.sort((a, b) -> {
            boolean pa = a.isPinned();
            boolean pb = b.isPinned();
            if (pa != pb) return pa ? -1 : 1;       // 고정이 위
            if (pa) {                                // 둘 다 고정 → 제목 가나다순
                String ta = a.getTitle() == null ? "" : a.getTitle();
                String tb = b.getTitle() == null ? "" : b.getTitle();
                return ta.compareTo(tb);
            }
            return 0; // 둘 다 비고정 → 기존 최신순 유지
        });

        List<AiChatSessionDto> out = new ArrayList<>(sessions.size());
        for (AiChatSession s : sessions) {
            AiChatMessage last = messageRepository
                .findFirstBySession_SessionIdOrderByCreatedAtDescMessageIdDesc(s.getSessionId())
                .orElse(null);
            out.add(AiChatSessionDto.from(s, last));
        }
        return out;
    }

    @Transactional
    public AiChatSessionDto createSession(MemberSession ms) {
        Member me = loadMember(ms);
        AiChatSession saved = sessionRepository.save(AiChatSession.createNew(me));
        return AiChatSessionDto.from(saved, null);
    }

    @Transactional
    public void deleteSession(MemberSession ms, Long sessionId) {
        AiChatSession s = loadAndAssertOwner(ms, sessionId);
        sessionRepository.delete(s);
    }

    /**
     * 세션 제목 수정 — 점 3개 메뉴 [제목 수정].
     *  - 빈 제목·길이 초과는 거절.
     *  - 본인 소유 세션만 (loadAndAssertOwner 가 검증).
     */
    @Transactional
    public AiChatSessionDto renameSession(MemberSession ms, Long sessionId, String newTitle) {
        if (newTitle == null || newTitle.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String trimmed = newTitle.trim();
        if (trimmed.length() > TITLE_USER_MAX_LEN) {
            trimmed = trimmed.substring(0, TITLE_USER_MAX_LEN);
        }
        AiChatSession s = loadAndAssertOwner(ms, sessionId);
        s.rename(trimmed);
        return AiChatSessionDto.from(s, null);
    }

    /**
     * 세션 고정/고정 해제 토글 — 점 3개 메뉴 [고정하기 / 고정 해제].
     *  - 본인 소유 세션만.
     *  - 반환: 새 pinned 상태.
     */
    @Transactional
    public boolean togglePinSession(MemberSession ms, Long sessionId) {
        AiChatSession s = loadAndAssertOwner(ms, sessionId);
        s.togglePin();
        return s.isPinned();
    }

    // ─── 메시지 ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AiChatMessageDto> findMessages(MemberSession ms, Long sessionId) {
        loadAndAssertOwner(ms, sessionId);
        return messageRepository
            .findBySession_SessionIdOrderByCreatedAtAscMessageIdAsc(sessionId)
            .stream().map(AiChatMessageDto::from).toList();
    }

    /**
     * 사용자 메시지 전송 → 동기적으로 LLM 호출 → ASSISTANT 메시지 저장.
     * 응답으로 ASSISTANT DTO 반환 (프론트가 즉시 말풍선 추가).
     *
     * 동작 순서:
     *  1. 참여자 검증
     *  2. Rate limit 체크 (오늘 USER 메시지 수 < cfg.rateLimitPerDay)
     *  3. USER 메시지 저장
     *  4. 첫 USER 메시지면 title 자동 갱신
     *  5. 시스템 프롬프트 + 최근 N개 히스토리 → LLM 호출
     *  6. ASSISTANT 메시지 저장 + 세션 touch
     *  7. ASSISTANT DTO 반환
     */
    @Transactional
    public AiChatMessageDto sendMessage(MemberSession ms, Long sessionId, String userContent) {
        if (userContent == null || userContent.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        AiChatSession session = loadAndAssertOwner(ms, sessionId);
        Member me = session.getMember();

        AiConfig cfg = aiConfigService.getCurrent();
        enforceRateLimit(ms, cfg);

        // 1. USER 메시지 저장
        AiChatMessage userMsg = messageRepository.save(
            AiChatMessage.user(session, userContent.trim()));

        // 2. 첫 사용자 메시지면 title 자동 갱신
        if ("새 대화".equals(session.getTitle())) {
            session.setTitle(truncate(userContent.trim(), TITLE_MAX_LEN));
        }

        // 3. LLM 호출용 메시지 리스트 준비 (시스템 + 최근 N개)
        List<LlmMessage> llmMessages = buildLlmMessages(ms, session, me, userContent);

        // 4. LLM 호출
        LlmResponse resp;
        try {
            resp = llmClientFactory.generate(llmMessages);
        } catch (Exception e) {
            // 429 (quota 초과) 는 별도 ErrorCode 로 친절한 안내
            if (e.getMessage() != null && e.getMessage().contains("HTTP 429")) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_QUOTA_EXCEEDED);
            }
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }

        // 5. ASSISTANT 응답에서 액션 제안 (일정/결재 JSON) 분리
        String rawContent = resp.content() != null ? resp.content() : "";
        AiProposalExtractor.Extracted ex = proposalExtractor.extract(rawContent);

        // Oracle 은 빈 문자열('')을 NULL 로 저장 → content NOT NULL 제약 위반(ORA-01400).
        // AI 가 본문 없이 액션 카드(JSON 블록)만 응답하면 cleanedContent 가 "" 가 되므로 안내 문구로 대체.
        String content = ex.cleanedContent() != null ? ex.cleanedContent().trim() : "";
        if (content.isBlank()) {
            content = ex.proposalJson() != null
                ? "아래 내용을 확인하고 진행해 주세요."
                : "죄송합니다. 답변을 생성하지 못했어요. 다시 시도해 주세요.";
        }

        AiChatMessage assistantMsg = AiChatMessage.assistant(session, content,
            resp.promptTokens(), resp.completionTokens());
        if (ex.proposalJson() != null) {
            // 휴가·지출 제안이면 결재자 이름에 부서/직급을 보강 → 카드에서 "홍길동(인사팀/부장)" 표시.
            String pType = ex.proposalType();
            String proposalJson = ("leave".equals(pType) || "expense".equals(pType))
                ? enrichApproverProposal(ex.proposalJson(), ms)
                : ex.proposalJson();
            assistantMsg.setProposalJson(proposalJson);
            assistantMsg.setProposalApplied(false);
        }
        assistantMsg = messageRepository.save(assistantMsg);

        // 6. 세션 갱신 시각 touch
        session.touch();

        return AiChatMessageDto.from(assistantMsg);
    }

    // ─── 액션 제안 확정 (일정 등록/수정/삭제) ───────────────────────

    /**
     * 사용자가 일정 제안 카드의 [등록/수정/삭제] 버튼을 누른 경우.
     * proposalJson 의 type 으로 분기 (calendar / calendar_update / calendar_delete).
     */
    @Transactional
    public AiChatMessageDto confirmCalendarProposal(MemberSession ms, Long messageId) {
        AiChatMessage msg = messageRepository.findById(messageId)
            .orElseThrow(() -> new BusinessException(ErrorCode.AI_SESSION_NOT_FOUND));
        AiChatSession session = loadAndAssertOwner(ms, msg.getSession().getSessionId());

        if (Boolean.TRUE.equals(msg.getProposalApplied())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        if (msg.getProposalJson() == null || msg.getProposalJson().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        AiCalendarProposalDto p;
        try {
            p = objectMapper.readValue(msg.getProposalJson(), AiCalendarProposalDto.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String type = p.type() == null ? "" : p.type().toLowerCase();
        String confirmText;
        try {
            switch (type) {
                case "calendar"        -> confirmText = applyCreate(ms, p);
                case "calendar_update" -> confirmText = applyUpdate(ms, p);
                case "calendar_delete" -> confirmText = applyDelete(ms, p);
                default -> throw new BusinessException(ErrorCode.INVALID_STATUS);
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }

        msg.setProposalApplied(true);
        AiChatMessage confirmMsg = messageRepository.save(
            AiChatMessage.assistant(session, confirmText, null, null));
        session.touch();
        return AiChatMessageDto.from(confirmMsg);
    }

    private String applyCreate(MemberSession ms, AiCalendarProposalDto p) {
        LocalDateTime startAt = parseIso(p.startAt());
        if (startAt == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        LocalDateTime endAt = parseIso(p.endAt());
        if (endAt == null) endAt = startAt.plusHours(1);

        com.team.intranet.dto.CalendarDto cdto = buildCalendarDto(ms, p, startAt, endAt);
        ShareInfo share = resolveAttendees(ms, p.attendeeNames());
        if (!share.memberIds.isEmpty()) {
            cdto.setVisibility(com.team.intranet.enums.Visibility.SPECIFIC);
            cdto.setShareMemberIds(share.memberIds);
        }

        calendarService.createCalendar(ms, cdto);
        return "✅ 일정이 등록되었습니다.\n• " + cdto.getTitle()
            + "\n• 시간: " + formatRange(startAt, endAt)
            + (notBlank(cdto.getLocation()) ? "\n• 장소: " + cdto.getLocation() : "")
            + share.toResultLine();
    }

    private String applyUpdate(MemberSession ms, AiCalendarProposalDto p) {
        if (p.calendarId() == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        LocalDateTime startAt = parseIso(p.startAt());
        LocalDateTime endAt   = parseIso(p.endAt());
        if (startAt == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        if (endAt == null) endAt = startAt.plusHours(1);

        com.team.intranet.dto.CalendarDto cdto = buildCalendarDto(ms, p, startAt, endAt);
        ShareInfo share = resolveAttendees(ms, p.attendeeNames());

        if (!share.memberIds.isEmpty()) {
            cdto.setVisibility(com.team.intranet.enums.Visibility.SPECIFIC);
            cdto.setShareMemberIds(share.memberIds);
        }

        calendarService.updateCalendar(ms, p.calendarId(), cdto);
        return "✏️ 일정이 수정되었습니다.\n• " + cdto.getTitle()
            + "\n• 시간: " + formatRange(startAt, endAt)
            + (notBlank(cdto.getLocation()) ? "\n• 장소: " + cdto.getLocation() : "")
            + share.toResultLine();
    }

    private com.team.intranet.dto.CalendarDto buildCalendarDto(MemberSession ms, AiCalendarProposalDto p,
                                                                LocalDateTime startAt, LocalDateTime endAt) {
        com.team.intranet.dto.CalendarDto cdto = new com.team.intranet.dto.CalendarDto();
        cdto.setTitle(p.title() != null ? p.title() : "AI 일정");
        cdto.setDescription(p.description());
        cdto.setStartAt(startAt);
        cdto.setEndAt(endAt);
        cdto.setAllDay(Boolean.TRUE.equals(p.allDay()));
        cdto.setLocation(p.location());
        cdto.setVisibility(com.team.intranet.enums.Visibility.PRIVATE);
        return cdto;
    }

    /**
     * AI 가 추출한 동료 이름을 회원 ID 로 매칭. 회사 격리 + 활성(JOIN) + 본인 제외.
     * 매칭된 이름과 누락된 이름을 분리해서 반환 → 사용자 메시지에 안내.
     */
    private ShareInfo resolveAttendees(MemberSession ms, java.util.List<String> names) {
        ShareInfo info = new ShareInfo();
        if (names == null || names.isEmpty()) return info;
        java.util.List<String> cleaned = names.stream()
            .filter(n -> n != null && !n.isBlank())
            .map(String::trim).distinct().toList();
        if (cleaned.isEmpty()) return info;

        java.util.List<com.team.intranet.entity.Member> matched = memberRepository
            .findByStatusAndCompanyCompanyIdAndNameIn(
                com.team.intranet.enums.member.Status.JOIN, ms.getCompanyId(), cleaned);

        java.util.Set<String> foundNames = new java.util.HashSet<>();
        for (com.team.intranet.entity.Member m : matched) {
            if (m.getMemberId().equals(ms.getMemberId())) continue; // 본인 제외
            info.memberIds.add(m.getMemberId());
            info.matchedNames.add(m.getName());
            foundNames.add(m.getName());
        }
        for (String n : cleaned) {
            if (!foundNames.contains(n)) info.missingNames.add(n);
        }
        return info;
    }

    /** 참여자 매칭 결과. */
    private static class ShareInfo {
        java.util.List<Long> memberIds = new java.util.ArrayList<>();
        java.util.List<String> matchedNames = new java.util.ArrayList<>();
        java.util.List<String> missingNames = new java.util.ArrayList<>();
        String toResultLine() {
            StringBuilder sb = new StringBuilder();
            if (!matchedNames.isEmpty()) {
                sb.append("\n• 공유: ").append(String.join(", ", matchedNames));
            }
            if (!missingNames.isEmpty()) {
                sb.append("\n• ⚠️ 회사 명단에서 못 찾음: ").append(String.join(", ", missingNames));
            }
            return sb.toString();
        }
    }

    private String applyDelete(MemberSession ms, AiCalendarProposalDto p) {
        if (p.calendarId() == null) throw new BusinessException(ErrorCode.INVALID_INPUT);

        // 삭제 전 일정 정보 조회 — 확정 메시지에 제목/시간 표시용.
        String title = p.title();
        String timeRange = null;
        if (title == null || title.isBlank()) {
            var existing = calendarRepository.findById(p.calendarId()).orElse(null);
            if (existing != null) {
                title = existing.getTitle();
                if (existing.getStartAt() != null) {
                    timeRange = formatRange(existing.getStartAt(), existing.getEndAt());
                }
            }
        }
        if (title == null || title.isBlank()) title = "일정";

        calendarService.deleteCalendar(ms, p.calendarId());

        StringBuilder msg = new StringBuilder("🗑️ '").append(title).append("' 일정을 삭제했습니다.");
        if (timeRange != null) msg.append("\n• 시간: ").append(timeRange);
        return msg.toString();
    }

    private static String formatRange(LocalDateTime start, LocalDateTime end) {
        if (end == null) return start.format(HUMAN_FMT);
        boolean sameDate = end.toLocalDate().equals(start.toLocalDate());
        return start.format(HUMAN_FMT) + " ~ " + (sameDate ? end.format(HUMAN_TIME_ONLY) : end.format(HUMAN_FMT));
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static LocalDateTime parseIso(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s); }
        catch (Exception e) { return null; }
    }

    // ─── 휴가 신청 제안 확정 (전자결재 기안) ─────────────────────────

    /**
     * 사용자가 휴가 신청 카드의 [신청] 버튼을 누른 경우.
     * proposalJson(leave) 파싱 → 결재선 직급명을 회원으로 매칭 → VACATION 양식으로 전자결재 기안.
     * 이후 승인/일정 자동 등록은 기존 전자결재 흐름이 처리한다. (AI 는 신청만 대행)
     *
     * @param vacationTypeOverride 카드 dropdown 에서 사용자가 최종 확정한 휴가 종류 코드.
     *                             비어 있으면 AI 추론값(proposal)을 사용.
     * @param attachmentIds 카드 하단에서 미리 업로드한 첨부파일 id (선택 사항, null/빈 리스트 허용).
     */
    @Transactional
    public AiChatMessageDto confirmLeaveProposal(MemberSession ms, Long messageId,
                                                 String vacationTypeOverride, List<Long> attachmentIds) {
        AiChatMessage msg = messageRepository.findById(messageId)
            .orElseThrow(() -> new BusinessException(ErrorCode.AI_SESSION_NOT_FOUND));
        AiChatSession session = loadAndAssertOwner(ms, msg.getSession().getSessionId());

        if (Boolean.TRUE.equals(msg.getProposalApplied())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        if (msg.getProposalJson() == null || msg.getProposalJson().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        AiLeaveProposalDto p;
        try {
            p = objectMapper.readValue(msg.getProposalJson(), AiLeaveProposalDto.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        LocalDate startDate = parseLocalDate(p.startDate());
        if (startDate == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        LocalDate endDate = parseLocalDate(p.endDate());
        if (endDate == null) endDate = startDate;

        // 휴가 종류 — 카드 dropdown 확정값 우선, 없으면 AI 추론값.
        String vacationType = (vacationTypeOverride != null && !vacationTypeOverride.isBlank())
            ? vacationTypeOverride.trim()
            : (p.vacationType() != null && !p.vacationType().isBlank() ? p.vacationType().trim() : "ETC");

        Member drafter = session.getMember();
        List<Long> approvalLine = resolveApproverLine(ms, p.approverNames());

        // VACATION 양식 — 회사 사본 우선, 없으면 시스템 디폴트.
        FormTemplate template = formTemplateRepository
            .findByCompanyAndFormCode(drafter.getCompany(), "VACATION")
            .or(() -> formTemplateRepository.findByCompanyIsNullAndFormCode("VACATION"))
            .orElseThrow(() -> new BusinessException(ErrorCode.FORM_TEMPLATE_NOT_FOUND));

        ApprovalSubmitRequest req = new ApprovalSubmitRequest();
        req.setFormTemplateId(template.getFormTemplateId());
        req.setFormCode("VACATION");
        req.setApprovalLine(approvalLine);
        req.setTitle(buildLeaveTitle(vacationType, startDate, p.reason()));
        req.setVacation(new VacationPayload(
            vacationType,
            p.totalDays() != null ? p.totalDays() : 1.0,
            startDate, endDate));
        req.setAttachmentIds(attachmentIds); // 선택 사항 — null/빈 리스트면 첨부 없음

        try {
            approvalService.submit(ms, req);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }

        msg.setProposalApplied(true);

        String approverNames = approvalLine.stream()
            .map(id -> memberRepository.findById(id).map(Member::getName).orElse("?"))
            .reduce((a, b) -> a + " → " + b).orElse("");
        Double days = p.totalDays() != null ? p.totalDays() : 1.0;
        String confirmText = "✅ 휴가 신청서가 결재 상신되었습니다."
            + "\n• " + req.getTitle()
            + "\n• 기간: " + formatLeaveRange(startDate, endDate) + " (" + days + "일)"
            + "\n• 결재선: " + approverNames;

        AiChatMessage confirmMsg = messageRepository.save(
            AiChatMessage.assistant(session, confirmText, null, null));
        session.touch();
        return AiChatMessageDto.from(confirmMsg);
    }

    // ─── 지출결의서 신청 제안 확정 (전자결재 기안) ───────────────────

    /**
     * 사용자가 지출결의서 카드의 [신청] 버튼을 누른 경우.
     * proposalJson(expense) 파싱 → 결재선 이름을 회원으로 매칭 → EXPENSE 양식으로 전자결재 기안.
     *
     * @param attachmentIds 카드 하단에서 미리 업로드한 첨부파일 id (선택 사항, null/빈 리스트 허용).
     */
    @Transactional
    public AiChatMessageDto confirmExpenseProposal(MemberSession ms, Long messageId,
                                                   List<Long> attachmentIds) {
        AiChatMessage msg = messageRepository.findById(messageId)
            .orElseThrow(() -> new BusinessException(ErrorCode.AI_SESSION_NOT_FOUND));
        AiChatSession session = loadAndAssertOwner(ms, msg.getSession().getSessionId());

        if (Boolean.TRUE.equals(msg.getProposalApplied())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        if (msg.getProposalJson() == null || msg.getProposalJson().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        AiExpenseProposalDto p;
        try {
            p = objectMapper.readValue(msg.getProposalJson(), AiExpenseProposalDto.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (p.amount() == null || p.amount() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        LocalDate spentAt = parseLocalDate(p.spentAt());
        if (spentAt == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        String category = (p.category() != null && !p.category().isBlank())
            ? p.category().trim() : "기타";
        String description = (p.description() != null && !p.description().isBlank())
            ? p.description().trim() : null;

        Member drafter = session.getMember();
        List<Long> approvalLine = resolveApproverLine(ms, p.approverNames());

        // EXPENSE 양식 — 회사 사본 우선, 없으면 시스템 디폴트.
        FormTemplate template = formTemplateRepository
            .findByCompanyAndFormCode(drafter.getCompany(), "EXPENSE")
            .or(() -> formTemplateRepository.findByCompanyIsNullAndFormCode("EXPENSE"))
            .orElseThrow(() -> new BusinessException(ErrorCode.FORM_TEMPLATE_NOT_FOUND));

        ApprovalSubmitRequest req = new ApprovalSubmitRequest();
        req.setFormTemplateId(template.getFormTemplateId());
        req.setFormCode("EXPENSE");
        req.setApprovalLine(approvalLine);
        req.setTitle(buildExpenseTitle(category, p.amount()));
        req.setExpense(new ExpensePayload(p.amount(), category, spentAt, description));
        req.setAttachmentIds(attachmentIds); // 선택 사항 — null/빈 리스트면 첨부 없음

        try {
            approvalService.submit(ms, req);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }

        msg.setProposalApplied(true);

        String approverNames = approvalLine.stream()
            .map(id -> memberRepository.findById(id).map(Member::getName).orElse("?"))
            .reduce((a, b) -> a + " → " + b).orElse("");
        String confirmText = "✅ 지출결의서가 결재 상신되었습니다."
            + "\n• " + req.getTitle()
            + "\n• 금액: " + formatWon(p.amount()) + "원"
            + "\n• 분류: " + category
            + "\n• 지출일: " + spentAt.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))
            + "\n• 결재선: " + approverNames;

        AiChatMessage confirmMsg = messageRepository.save(
            AiChatMessage.assistant(session, confirmText, null, null));
        session.touch();
        return AiChatMessageDto.from(confirmMsg);
    }

    private String buildExpenseTitle(String category, Long amount) {
        return "[지출결의] " + category + " " + formatWon(amount) + "원";
    }

    /** 금액에 천 단위 구분 쉼표. */
    private static String formatWon(Long amount) {
        return amount == null ? "0" : String.format("%,d", amount);
    }

    /**
     * 결재자 이름 배열 → 회원 ID 결재선. 사용자가 대화로 정한 순서를 그대로 결재 순서로 사용.
     *  - 회사 + 활성(JOIN) 회원 중 이름이 정확히 일치하는 회원으로 매칭.
     *  - 매칭 0명 → MEMBER_NOT_FOUND, 동명이인(2명+) → INVALID_INPUT (AI 가 부서로 재확인하도록 유도).
     */
    private List<Long> resolveApproverLine(MemberSession ms, List<String> approverNames) {
        if (approverNames == null || approverNames.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<String> cleaned = approverNames.stream()
            .filter(n -> n != null && !n.isBlank())
            .map(String::trim).toList();
        if (cleaned.isEmpty()) throw new BusinessException(ErrorCode.INVALID_INPUT);

        List<Member> matched = memberRepository.findByStatusAndCompanyCompanyIdAndNameIn(
            Status.JOIN, ms.getCompanyId(), cleaned);

        List<Long> line = new ArrayList<>();
        for (String name : cleaned) {
            List<Member> hits = matched.stream().filter(m -> name.equals(m.getName())).toList();
            if (hits.isEmpty()) throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
            if (hits.size() > 1) throw new BusinessException(ErrorCode.INVALID_INPUT); // 동명이인
            Long id = hits.get(0).getMemberId();
            if (!line.contains(id)) line.add(id);
        }
        return line;
    }

    /**
     * 휴가·지출 제안 JSON 의 approverNames 를 회원 매칭해 카드 표시용 approvers 배열을 보강한다.
     *  - approvers: [{name, dept, position, matched}] — 카드가 "홍길동(인사팀/부장)" 렌더에 사용.
     *  - 매칭 실패/동명이인은 matched=false 로 표시만 하고, 실제 검증은 confirm 단계에서 수행.
     */
    private String enrichApproverProposal(String proposalJson, MemberSession ms) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(proposalJson);
            if (!root.isObject()) return proposalJson;
            com.fasterxml.jackson.databind.JsonNode namesNode = root.get("approverNames");
            if (namesNode == null || !namesNode.isArray() || namesNode.isEmpty()) return proposalJson;

            List<String> names = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode n : namesNode) {
                if (n.isTextual() && !n.asText().isBlank()) names.add(n.asText().trim());
            }
            if (names.isEmpty()) return proposalJson;

            List<Member> matched = memberRepository.findByStatusAndCompanyCompanyIdAndNameIn(
                Status.JOIN, ms.getCompanyId(), names);

            com.fasterxml.jackson.databind.node.ArrayNode approvers = objectMapper.createArrayNode();
            for (String name : names) {
                List<Member> hits = matched.stream().filter(m -> name.equals(m.getName())).toList();
                com.fasterxml.jackson.databind.node.ObjectNode o = objectMapper.createObjectNode();
                o.put("name", name);
                if (hits.size() == 1) {
                    Member m = hits.get(0);
                    o.put("dept", m.getDept() != null ? m.getDept().getDeptName() : null);
                    o.put("position", m.getPosition() != null ? m.getPosition().getPositionName() : null);
                    o.put("matched", true);
                } else {
                    o.put("matched", false); // 미매칭 or 동명이인
                }
                approvers.add(o);
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) root).set("approvers", approvers);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return proposalJson;
        }
    }

    private String buildLeaveTitle(String vacationType, LocalDate startDate, String reason) {
        String label = leaveTypeLabel(vacationType);
        String base = "[" + label + "] " + startDate.format(DateTimeFormatter.ofPattern("M월 d일")) + " 휴가";
        if (reason != null && !reason.isBlank()) base += " - " + reason.trim();
        return base;
    }

    /** VacationType 코드 → 한글 라벨. 코드가 아니면 입력값을 그대로 반환. */
    private static String leaveTypeLabel(String code) {
        if (code == null || code.isBlank()) return "휴가";
        try { return VacationType.valueOf(code.trim()).getDescription(); }
        catch (Exception e) { return code.trim(); }
    }

    private static String formatLeaveRange(LocalDate start, LocalDate end) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy년 M월 d일");
        if (end == null || end.equals(start)) return start.format(f);
        return start.format(f) + " ~ " + end.format(f);
    }

    private static LocalDate parseLocalDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim()); }
        catch (Exception e) { return null; }
    }

    // ─── 내부 ───────────────────────────────────────────────────────

    private Member loadMember(MemberSession ms) {
        if (ms == null || ms.getMemberId() == null) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        return memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private AiChatSession loadAndAssertOwner(MemberSession ms, Long sessionId) {
        AiChatSession s = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.AI_SESSION_NOT_FOUND));
        // 본인 세션인지 + 같은 회사인지 이중 검증 (회사 격리 원칙).
        if (!s.getMember().getMemberId().equals(ms.getMemberId())) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        if (s.getCompany() == null || !s.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return s;
    }

    /** 회원의 오늘 USER 메시지 수가 cfg.rateLimitPerDay 이상이면 거부. */
    private void enforceRateLimit(MemberSession ms, AiConfig cfg) {
        if (cfg.getRateLimitPerDay() == null || cfg.getRateLimitPerDay() <= 0) return;
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long todayCount = messageRepository.countByMemberAndRoleSince(
            ms.getMemberId(), AiChatRole.USER, startOfDay);
        if (todayCount >= cfg.getRateLimitPerDay()) {
            throw new BusinessException(ErrorCode.AI_RATE_LIMIT_EXCEEDED);
        }
    }

    /**
     * 시스템 프롬프트 + 최근 N개 메시지 → LlmMessage 리스트.
     * 최신 N개를 가져온 뒤 역순(시간 오름차순)으로 정렬.
     * userContent + 직전 ASSISTANT 의 proposalJson 으로 게시판/일정 컨텍스트 lazy 첨부 여부 결정.
     */
    private List<LlmMessage> buildLlmMessages(MemberSession ms, AiChatSession session, Member me,
                                              String userContent) {
        List<LlmMessage> out = new ArrayList<>();

        List<AiChatMessage> recent = messageRepository
            .findBySession_SessionIdOrderByCreatedAtDescMessageIdDesc(
                session.getSessionId(), PageRequest.of(0, HISTORY_LIMIT));
        Collections.reverse(recent);

        boolean includeLeave    = needsLeaveContext(userContent, recent);
        boolean includeExpense  = needsExpenseContext(userContent, recent);
        boolean includeBoard    = includeLeave
            || (userContent != null && BOARD_KEYWORDS.matcher(userContent).find());
        boolean includeCalendar = includeLeave || needsCalendarContext(userContent, recent);
        out.add(LlmMessage.system(
            buildSystemPrompt(ms, me, includeBoard, includeCalendar, includeLeave, includeExpense)));

        for (AiChatMessage m : recent) {
            String content = m.getContent() == null ? "" : m.getContent();
            switch (m.getRole()) {
                case USER      -> out.add(LlmMessage.user(content));
                case ASSISTANT -> {
                    // ASSISTANT 가 직전에 한 액션 제안(JSON)도 함께 첨부 → 다음 턴에서 attendeeNames 등 유지 가능.
                    if (m.getProposalJson() != null && !m.getProposalJson().isBlank()) {
                        content = content + "\n[직전 액션 제안 JSON: " + m.getProposalJson() + "]";
                    }
                    out.add(LlmMessage.assistant(content));
                }
                case SYSTEM    -> { /* 저장된 SYSTEM 은 무시 — 위에서 한 번만 주입 */ }
            }
        }
        return out;
    }

    /**
     * 캘린더 컨텍스트 첨부 여부:
     *  - 사용자 메시지에 일정/시간 관련 키워드가 있거나
     *  - 직전 ASSISTANT 가 calendar 제안 카드를 띄운 상태 (=보완 대화 중) 면 첨부.
     */
    private boolean needsCalendarContext(String userContent, List<AiChatMessage> recent) {
        if (userContent != null && CALENDAR_KEYWORDS.matcher(userContent).find()) return true;
        for (int i = recent.size() - 1; i >= 0; i--) {
            AiChatMessage m = recent.get(i);
            if (m.getRole() == AiChatRole.ASSISTANT) {
                return m.getProposalJson() != null && !m.getProposalJson().isBlank();
            }
        }
        return false;
    }

    /**
     * 휴가 컨텍스트 첨부 여부 — 한 번 시작된 휴가 흐름은 카드가 뜰 때까지 안정적으로 유지한다.
     *  - 이번 사용자 메시지에 휴가 키워드가 있거나
     *  - 최근 대화 묶음(USER 메시지 또는 json:leave 카드) 안에 휴가 흐름이 진행 중이면 첨부.
     * 직전 ASSISTANT 1건만 보면 키워드 없는 중간 턴에서 안내가 끊겼기 때문에 묶음 전체를 스캔한다.
     */
    private boolean needsLeaveContext(String userContent, List<AiChatMessage> recent) {
        if (userContent != null && LEAVE_KEYWORDS.matcher(userContent).find()) return true;
        for (AiChatMessage m : recent) {
            if (m.getRole() == AiChatRole.USER) {
                String c = m.getContent();
                if (c != null && LEAVE_KEYWORDS.matcher(c).find()) return true;
            } else if (m.getRole() == AiChatRole.ASSISTANT) {
                String pj = m.getProposalJson();
                if (pj != null && pj.contains("\"type\":\"leave\"")) return true;
            }
        }
        return false;
    }

    /**
     * 지출 컨텍스트 첨부 여부 — 한 번 시작된 지출 흐름은 카드가 뜰 때까지 안정적으로 유지한다.
     *  - 이번 사용자 메시지에 지출 키워드가 있거나
     *  - 최근 대화 묶음(USER 메시지 또는 json:expense 카드) 안에 지출 흐름이 진행 중이면 첨부.
     * 직전 ASSISTANT 1건만 보면 키워드 없는 중간 턴에서 안내가 끊겼기 때문에 묶음 전체를 스캔한다.
     */
    private boolean needsExpenseContext(String userContent, List<AiChatMessage> recent) {
        if (userContent != null && EXPENSE_KEYWORDS.matcher(userContent).find()) return true;
        for (AiChatMessage m : recent) {
            if (m.getRole() == AiChatRole.USER) {
                String c = m.getContent();
                if (c != null && EXPENSE_KEYWORDS.matcher(c).find()) return true;
            } else if (m.getRole() == AiChatRole.ASSISTANT) {
                String pj = m.getProposalJson();
                if (pj != null && pj.contains("\"type\":\"expense\"")) return true;
            }
        }
        return false;
    }

    private String buildSystemPrompt(MemberSession ms, Member me, boolean includeBoard,
                                     boolean includeCalendar, boolean includeLeave,
                                     boolean includeExpense) {
        String position    = me.getPosition() != null ? me.getPosition().getPositionName() : "직원";
        String dept        = me.getDept()     != null ? me.getDept().getDeptName()        : "미지정";
        String companyName = me.getCompany()  != null ? me.getCompany().getCompanyName()  : "(미지정)";
        Long companyId     = me.getCompany()  != null ? me.getCompany().getCompanyId()    : null;

        // ───────── 사용자 역할/권한 요약 (LLM 에 명시) ─────────
        // 시스템 프롬프트만으론 우회 가능하므로 이 라벨은 보조 가이드. 1차 보호는 데이터 소스 권한 필터.
        String roleLabel = roleLabelFor(ms.getRole());
        String permLabel = permissionsLabelFor(ms);

        String basePrompt = """
            당신은 한국 회사 인트라넷의 AI 비서입니다.
            - 한국어로 정중하고 간결하게 답변하세요.
            - 사용자 정보: 이름=%s, 회사=%s, 직급=%s, 부서=%s
            - 사용자 역할: %s
            - 사용자 보유 권한: %s
            - 오늘 날짜: %s

            🚫 팩트 기반 응답 원칙 (절대 위반 금지 — 최우선):
            - 아래 첨부된 데이터(게시판/일정/회원 명단/휴가 양식 등) 안의 사실만 인용하세요.
            - 데이터에 없는 내용은 "확인되지 않습니다" 라고만 답하세요. 추측·창작·일반 상식 보충 절대 금지.
            - "아마", "추측컨대", "보통", "일반적으로", "대체로" 같은 어휘로 사실 외 내용을 만들지 마세요.
            - 외부 지식(인터넷 정보·일반 통계·뉴스) 으로 빈자리를 채우지 마세요.
            - 사내 규정·정책·복지·급여 체계 등은 시스템에 등록된 자료(게시판 공지 등)에만 근거하세요.
              등록되지 않은 규정은 "사내 공지에 등록되지 않은 내용입니다" 라고 답하세요.
            - 게시글 제목/내용/작성자, 회원 이름/직급/부서, 일정 제목/시간을 지어내지 마세요.

            🔐 권한 기반 접근 통제 (회원이 못 보는 정보 = AI 도 못 봄):
            - 사용자 본인이 인트라넷 화면에서 직접 볼 수 없는 정보는 AI 도 절대 답하지 마세요.
            - "조회(읽기)" 와 "수정/삭제(쓰기)" 는 권한 정책이 다릅니다. 사용자가 무엇을 원하는지 먼저 구분하세요.

              [조회 — 권한자는 위계 무관 회사 전체 가능]
              · 다른 회원의 개인정보(연락처 / 이메일 / 생년월일 / 직급 / 부서 / 입사일 등) →
                MEMBER_MANAGEMENT 권한 또는 ADMIN/MASTER 면 회사 내 누구든 조회 가능 (위계 무관 — 본인보다 상위 직급도 OK).
                권한 없으면 본인 정보만 응답.
              · 다른 회원의 결재 내역(승인 / 반려 / 대기 / 양식) →
                본인이 기안자이거나 결재선에 포함된 경우만 조회 가능.
              · 회사 전체 근태 (출/퇴근 시각, 야근 시간 등) →
                ATTENDANCE_MANAGEMENT 권한 또는 ADMIN/MASTER 면 회사 전체 조회 가능 (위계 무관).
                권한 없으면 본인 근태만.
              · 다른 회원의 비공개 일정 → 본인 일정 또는 본인이 공유받은 일정만 답변 가능.
              · 관리자 전용 통계/대시보드/매출/회사 운영 데이터 → ADMIN/MASTER 만.
              · 다른 회사의 모든 데이터 → 회사 격리, 절대 응답 거부.

              [수정 / 삭제 — 위계 보호 적용]
              · 다른 회원의 정보·상태(휴직 / 퇴사 등) 변경 요청 →
                MEMBER_MANAGEMENT 권한 보유자라도 **자기보다 낮은 직급만** 수정 가능. 상위·동등 직급은 거부.
              · ADMIN / MASTER 는 위계 무관 변경 가능.
              · 일반 회원은 본인 정보만 수정 가능.

            - 사용자가 "내 정보"·"내 일정"·"내 휴가" 처럼 본인 정보를 요청하면 첨부 데이터 안에서만 답하세요.
            - 권한 거부 시 우회 방법(예: "관리자에게 물어보세요") 외엔 다른 정보를 추가하지 마세요.

            👥 답변은 일반 직장인 입장에서 이해할 수 있는 자연스러운 말로 작성하세요:
            - "id", "calendarId", "boardId", "row" 같은 개발자/시스템 용어 금지.
            - 영문 컬럼명, 따옴표로 감싼 코드, 괄호 안 숫자 식별자(예: "(id=42)") 사용 금지.
            - 일정/게시글을 지칭할 때는 제목/날짜/장소 등 사람이 알아볼 수 있는 정보만 사용.
            - 시간은 "2026년 5월 20일 12:30" 같은 한국어 형식으로.
            - JSON 블록 안의 필드명은 형식이므로 유지하되, 본문 답변에는 절대 노출 X.

            ⚠️ 회사 격리 원칙 (절대 위반 금지):
            - 답변에 사용 가능한 데이터는 위 사용자 회사("%s") 범위 내로 제한됩니다.
            - 다른 회사의 게시글/일정/회원 정보는 절대 참조하지 마세요.

            📋 게시판 답변 원칙:
            - 사용자가 게시판/공지/게시글 관련 질문을 하면, 아래 "참고 가능 게시판 데이터" 에 있는 게시판만 활용하세요.
            - 사용자 질문이 어떤 게시판 종류(공지사항/자유게시판/복지/익명 등)에 관한지 먼저 판단하고,
              해당 게시판의 게시글을 우선적으로 참고해 답변하세요.
            - 데이터에 나열되지 않은 게시판에 대한 질문은 "권한이 없거나 AI 가 접근할 수 없는 게시판입니다." 라고 답하세요.
            - 추측·창작 금지. 데이터에 있는 내용만 인용하세요.

            📅 일정 관리 (등록 / 수정 / 삭제):
            ⚠️ 휴가 신청은 일정(calendar)이 아닙니다 — 절대 혼동 금지:
            - "휴가/연차/반차/월차/병가/경조사/휴직" 신청 의도가 보이면 json:calendar 블록을 만들지 마세요.
            - 휴가 신청은 아래 "🏖️ 휴가 신청" 절차에 따라 오직 json:leave 블록으로만 처리합니다.
            - 결재자(결재선) 성함을 attendeeNames(참여자)에 넣지 마세요. 결재선은 json:leave 의 approverNames 입니다.
            - 휴가가 승인되면 일정은 전자결재 시스템이 자동 등록합니다. AI 가 일정으로 등록하지 마세요.
            • 자연어 시간 해석 — 모호한 표현도 그대로 등록하세요. 사용자에게 정확한 시간을 다시 묻지 마세요:
                "아침"     → 08:00 ~ 09:00
                "오전"     → 10:00 ~ 11:00
                "점심"     → 12:00 ~ 13:00
                "오후"     → 15:00 ~ 16:00
                "저녁"     → 18:00 ~ 19:00
                "밤"       → 20:00 ~ 21:00
                "새벽"     → 06:00 ~ 07:00
                시각 자체가 전혀 없으면 → 09:00 ~ 10:00
                "내일/모레/이번주 X요일" 등도 오늘 날짜 기준으로 계산해서 그대로 등록.
            • 짧은 자연어 확인 문장 + 아래 JSON 블록 1개를 함께 출력. 등록·수정·삭제 외엔 절대 블록을 출력하지 마세요.

            [신규 등록]
            ```json:calendar
            {
              "title": "친구와 식사",
              "startAt": "2026-05-20T19:00:00",
              "endAt":   "2026-05-20T21:00:00",
              "location": "하남",
              "description": null,
              "allDay": false,
              "attendeeNames": ["테스터"]
            }
            ```
            • attendeeNames: 같이 참여하는 **회사 동료** 이름 배열. 아래 "회사 동료 명단" 의 이름을 그대로 써야 매칭됩니다.
              회사 동료가 아닌 외부인(예: "친구", "가족") 은 attendeeNames 에 넣지 마세요.
              참여자 없으면 [] 또는 생략.

            [수정 — "본인 일정" 목록의 id 그대로 사용. 변경할 필드만 바꾸고 나머지(공유 대상 포함)는 기존 값을 그대로 채워서 보내세요]
            ```json:calendar_update
            {
              "calendarId": 42,
              "title": "친구와 식사",
              "startAt": "2026-05-20T20:00:00",
              "endAt":   "2026-05-20T22:00:00",
              "location": "하남",
              "description": null,
              "allDay": false,
              "attendeeNames": ["홍길동"]
            }
            ```
            ⚠️ "본인 일정" 목록의 "공유=[...]" 또는 직전 등록 제안 JSON 의 attendeeNames 를 **그대로 attendeeNames 에 복사**하세요.
              누락하면 공유 대상이 모두 해제됩니다. 사용자가 명시적으로 빼달라고 한 경우에만 제외.

            [삭제 — calendarId 와 함께 title/startAt/공유 대상도 채워서 보내세요. 사용자가 카드에서 어떤 일정인지·누구와 공유한 일정인지 알아볼 수 있도록.]
            ```json:calendar_delete
            {
              "calendarId": 42,
              "title": "홍길동님과 식사",
              "startAt": "2026-05-20T12:00:00",
              "endAt": "2026-05-20T13:00:00",
              "location": "하남",
              "attendeeNames": ["홍길동"]
            }
            ```
            ⚠️ "본인 일정" 목록의 "공유=[...]" 값을 그대로 attendeeNames 에 복사하세요. 표시용입니다 (삭제 동작엔 영향 없음).

            • 수정/삭제는 반드시 아래 "본인 일정" 목록에 있는 id 만 사용. 없으면 "해당 일정이 없습니다" 라고만 답하고 JSON 출력 금지.
            • 사용자가 어떤 일정을 가리키는지 모호하면 한 줄로 물어보고 JSON 은 생략.
            • ⚠️ 한 답변에 JSON 블록은 정확히 1개. "내일 일정 다 지워줘" 처럼 여러 건 처리가 필요하면
              가장 가까운 1건만 JSON 으로 제안하고, 나머지는 본문에 "추가로 X건이 있습니다. 하나씩 처리하시겠어요?" 라고 안내하세요.

            🔄 미확정 등록 제안의 대화 컨텍스트 유지 (매우 중요):
            • 직전 ASSISTANT 응답에 `json:calendar` 블록 (신규 등록 제안) 이 있었고
              사용자가 아직 [등록] 을 누르지 않은 상태에서 추가 정보(참여자/시간/장소 변경 등) 를 보내면,
              **이건 "기존 일정 수정" 이 아니라 "직전 제안의 보완"** 입니다.
              → 절대 "해당 일정을 찾을 수 없다" 라고 답하지 마세요.
              → 직전 제안의 값을 그대로 가져와 보완 정보만 반영한 **새 `json:calendar` 블록** 을 다시 출력하세요.
            • 예시 흐름:
                USER:      "내일 기타 연습하기로 했어"
                ASSISTANT: "네, 등록할까요?" + json:calendar { title:"기타 연습", ... }   (← DB 저장 X, 카드만)
                USER:      "아 추가로 홍길동씨하고 같이"
                ASSISTANT: "네, 홍길동님 추가해서 다시 제안드립니다." + json:calendar { title:"기타 연습", ..., attendeeNames:["홍길동"] }
            • 본인 일정 목록(아래) 은 DB 에 저장 완료된 일정만 포함합니다. 등록 제안 단계의 일정은 거기 없습니다.
            • 반대로 사용자가 "수정해줘 / 변경해줘 / 옮겨줘" 처럼 명시적으로 수정을 요청하고 본인 일정 목록에 있으면 `json:calendar_update` 를 사용.
            """.formatted(
                me.getName(), companyName, position, dept,
                roleLabel, permLabel,
                LocalDate.now().toString(),
                companyName);

        // 회사 게시판 / 본인 일정 / 휴가 컨텍스트는 lazy 첨부 — 토큰 절약.
        // 게시판은 ms 를 전달해 ACL(canRead) 까지 검사 → 회원이 못 보는 게시판은 AI 컨텍스트에도 미포함.
        String boardContext    = (includeBoard && companyId != null)
            ? aiBoardContextService.buildContext(ms) : "";
        String calendarContext = includeCalendar
            ? aiCalendarContextService.buildContext(me.getMemberId()) : "";
        String leaveContext    = includeLeave ? buildLeaveGuide(ms) : "";
        String expenseContext  = includeExpense ? buildExpenseGuide(ms) : "";
        return basePrompt + boardContext + calendarContext + leaveContext + expenseContext;
    }

    /** 휴가 신청 안내 + 휴가 종류 목록 + 결재 가능 회원 명단. includeLeave 일 때만 첨부. */
    private String buildLeaveGuide(MemberSession ms) {
        StringBuilder sb = new StringBuilder("""


            🏖️ 휴가 신청 — AI 비서를 통한 전자결재 상신 (단계별 진행):

            [1단계] 휴가 정보 수집
            • 사용자가 휴가 신청을 원하면 "사유" 와 "결재선" 을 함께 물어보세요.
            • 휴가 종류는 묻지 마세요. 종류는 카드의 dropdown 에서 사용자가 직접 고릅니다.
              사용자가 자발적으로 종류를 말하면 그 값을 vacationType 추론에만 반영하세요.
            • 사용자가 "가능한지" 만 물으면 위 "본인 일정" 과 비교해 충돌 여부만 답하세요.
            • 기간은 "다음주 금요일" 등 자연어를 오늘 날짜 기준으로 계산하세요.

            [2단계] 결재선 확정 — ⚠️ 절대 임의로 만들지 마세요.
            • 결재선은 결재자 "회원" 으로 지정됩니다. 사용자에게 결재자 성함을
              1단계(첫 결재)부터 순서대로 정확히 물어보세요.
            • 위 "참고 가능 게시판 데이터" 의 공지/사내정책에 휴가 결재선 규칙이 있으면 사용자에게 안내해도 됩니다.
            • 사용자가 말한 순서를 그대로 1단계 → 최종 결재 순서로 사용하세요.
            • approverNames 에는 사용자가 알려준 결재자 성함을 그대로 담으세요. 입력된 이름은
              시스템이 회원 명단과 자동 대조하며, 잘못된 이름·동명이인은 카드에 경고로 표시됩니다.
            • 결재자 관련 추가 안내는 아래 "결재자 안내" 항목을 따르세요.
            • ⚠️ 결재선을 추측하거나 예시 이름을 복사하지 마세요. 결재선이 확정되기 전에는
              json:leave 블록을 출력하지 말고 본문으로 계속 되물으세요.

            [3단계] 카드 출력
            • 기간·사유 + 확정된 결재선이 모두 모이면 짧은 확인 문장 + json:leave 블록 1개 출력.
            • ⚠️ 휴가 신청 카드는 반드시 json:leave 블록만 사용하세요. json:calendar 블록은 절대 출력 금지.
            • AI 는 자동 제출하지 않습니다. 카드만 제시하고 사용자가 [신청] 버튼을 누릅니다.
            • [신청] 버튼을 누르면 전자결재가 상신되고 1단계 결재자에게 알림이 갑니다.
              최종 승인 후 일정 등록까지는 전자결재 시스템이 처리하므로 AI 가 일정을 따로 만들지 마세요.

            ```json:leave
            {
              "vacationType": "ANNUAL_PAID_LEAVE",
              "startDate": "2026-05-29",
              "endDate": "2026-05-29",
              "totalDays": 1,
              "reason": "개인 사유",
              "approverNames": ["(1단계 결재자 성함)", "(최종 결재자 성함)"]
            }
            ```
            • vacationType: 사용자가 종류를 말했으면 아래 "휴가 종류" 코드 중 하나로 추론,
              안 말했으면 "ANNUAL_PAID_LEAVE". (최종 종류는 사용자가 카드 dropdown 에서 확정.)
            • startDate / endDate: "YYYY-MM-DD". 하루면 둘을 같게.
            • totalDays: 일수 (반차는 0.5).
            • reason: 사유. 사용자가 말하지 않으면 "개인 사유".
            • approverNames: 2단계에서 확정된 결재자 성함을 결재 순서대로. 아래 회원 목록의
              이름과 정확히 일치해야 합니다. 확정 안 됐으면 블록을 출력하지 마세요.

            [휴가 종류 — vacationType 추론용 코드]
            """);
        for (VacationType vt : VacationType.values()) {
            sb.append("- ").append(vt.name()).append(" : ").append(vt.getDescription()).append("\n");
        }

        appendApproverCandidates(sb, ms);
        return sb.toString();
    }

    /** 지출결의서 신청 안내 + 결재 가능 회원 명단. includeExpense 일 때만 첨부. */
    private String buildExpenseGuide(MemberSession ms) {
        StringBuilder sb = new StringBuilder("""


            🧾 지출결의서 신청 — AI 비서를 통한 전자결재 상신 (단계별 진행):

            [1단계] 지출 정보 수집 — 필수 항목: 금액 · 분류 · 지출일 · 결재선 (상세 내역은 선택)
            • ⚠️ "추가로 필요한 정보가 있나요?" 처럼 모호하게 묻지 마세요. 사용자는 무엇이 더
              필요한지 모릅니다. 아직 받지 못한 필수 항목을 콕 집어 직접 물어보세요.
              (예: "금액과 결재선을 알려주세요." / "결재자 성함을 알려주세요.")
            • 분류는 식대/교통비/출장비/회식비/비품 등 자유 텍스트입니다.
            • 지출일은 "어제", "지난주 금요일" 등 자연어를 오늘 날짜 기준으로 계산하세요.
            • 상세 내역은 선택 사항 — 사용자가 말하지 않으면 빈 문자열로 두고 굳이 다시 묻지 마세요.

            [2단계] 결재선 확정 — ⚠️ 절대 임의로 만들지 말고, 반드시 명시적으로 물어보세요.
            • 금액·분류·지출일을 받았으면 다음으로 결재선(결재자 성함)을 **반드시 직접 물어보세요.**
              결재선 없이는 결재를 올릴 수 없습니다. 사용자가 먼저 말해줄 거라 가정하지 마세요.
            • 결재선은 결재자 "회원" 으로 지정됩니다. 1단계(첫 결재)부터 순서대로 성함을 받으세요.
            • 사용자가 말한 순서를 그대로 1단계 → 최종 결재 순서로 사용하세요.
            • approverNames 에는 사용자가 알려준 결재자 성함을 그대로 담으세요. 입력된 이름은
              시스템이 회원 명단과 자동 대조하며, 잘못된 이름·동명이인은 카드에 경고로 표시됩니다.
            • 결재자 관련 추가 안내는 아래 "결재자 안내" 항목을 따르세요.
            • ⚠️ 결재선을 추측하거나 예시 이름을 복사하지 마세요. 결재선이 확정되기 전에는
              json:expense 블록을 출력하지 말고 본문으로 계속 되물으세요.

            [3단계] 카드 출력
            • 금액·분류·지출일 + 확정된 결재선이 모두 모이면 짧은 확인 문장 + json:expense 블록 1개 출력.
            • ⚠️ 지출결의서 카드는 반드시 json:expense 블록만 사용하세요.
              json:calendar·json:leave 블록은 절대 출력 금지 — 지출결의서는 일정도 휴가도 아닙니다.
            • AI 는 자동 제출하지 않습니다. 카드만 제시하고 사용자가 [신청] 버튼을 누릅니다.
            • [신청] 버튼을 누르면 전자결재가 상신되고 1단계 결재자에게 알림이 갑니다.

            ```json:expense
            {
              "amount": 50000,
              "category": "식대",
              "spentAt": "2026-05-20",
              "description": "팀 점심 회식",
              "approverNames": ["(1단계 결재자 성함)", "(최종 결재자 성함)"]
            }
            ```
            • amount: 지출 금액(원). 숫자만 (쉼표·"원" 제외). 확정 안 됐으면 블록을 출력하지 마세요.
            • category: 지출 분류 (식대/교통비/출장비 등). 사용자가 말하지 않으면 "기타".
            • spentAt: 지출일 "YYYY-MM-DD".
            • description: 상세 내역. 사용자가 말하지 않으면 빈 문자열("").
            • approverNames: 2단계에서 확정된 결재자 성함을 결재 순서대로. 아래 회원 목록의
              이름과 정확히 일치해야 합니다. 확정 안 됐으면 블록을 출력하지 마세요.
            """);
        appendApproverCandidates(sb, ms);
        return sb.toString();
    }

    /** 결재 가능 회원 명단을 system prompt 에 첨부 (휴가·지출 공통). */
    private void appendApproverCandidates(StringBuilder sb, MemberSession ms) {
        sb.append("\n[결재자 안내]\n");
        List<ApproverCandidate> candidates;
        try {
            candidates = approvalService.listApproverCandidates(ms);
        } catch (Exception e) {
            candidates = java.util.Collections.emptyList();
        }
        if (candidates.isEmpty()) {
            sb.append("결재 가능한 회원이 없습니다. 사용자에게 결재선 구성이 불가함을 안내하세요.\n");
        } else if (candidates.size() <= LEAVE_CANDIDATE_LIST_LIMIT) {
            // 인원이 적으면 전체 명단 노출 — AI 가 추천/확인에 활용.
            sb.append("아래는 결재 가능한 회원 목록입니다. 사용자가 결재자를 헷갈려하면 안내하세요.\n");
            for (ApproverCandidate c : candidates) {
                String dept = c.getDeptName() != null ? c.getDeptName() : "미지정";
                String pos  = c.getPositionName() != null ? c.getPositionName() : "미지정";
                sb.append("- ").append(c.getName())
                  .append(" (").append(dept).append(" / ").append(pos).append(")\n");
            }
        } else {
            // 인원이 많으면 명단을 통째로 넣지 않음 — 토큰 절약. 이름은 시스템이 검증.
            sb.append("회사 인원이 많아 전체 결재자 명단은 생략합니다 (총 ")
              .append(candidates.size()).append("명).\n")
              .append("사용자에게 결재자 성함을 직접 물어보세요. 입력된 이름은 시스템이 자동 검증합니다.\n")
              .append("사용자가 결재자를 모르면 전자결재 페이지에서 확인하도록 안내하세요.\n");
        }
    }

    private static String truncate(String s, int n) {
        if (s == null) return "새 대화";
        String oneLine = s.replaceAll("\\s+", " ").trim();
        if (oneLine.isEmpty()) return "새 대화";
        return oneLine.length() <= n ? oneLine : oneLine.substring(0, n) + "…";
    }

    /**
     * 사용자 역할을 시스템 프롬프트에 노출할 한국어 라벨로.
     *  - LLM 이 권한 분기를 더 잘 따르도록 사람이 읽는 형태로.
     */
    private static String roleLabelFor(com.team.intranet.enums.member.Role role) {
        if (role == null) return "일반 회원";
        return switch (role) {
            case MASTER    -> "MASTER (시스템 관리자)";
            case ADMIN     -> "ADMIN (회사 대표) — 회사 내 모든 정보 조회 가능";
            case SUB_ADMIN -> "SUB_ADMIN (부분 관리자) — 부여된 권한 범위 내 조회 가능";
            case USER      -> "USER (일반 회원) — 본인 정보 및 공개 자료만 조회 가능";
            default        -> role.name();
        };
    }

    /**
     * 사용자가 보유한 SUB_ADMIN 세부 권한 목록을 시스템 프롬프트용 한국어로.
     *  - ADMIN / MASTER 는 모든 권한을 갖는 것으로 표시 (실제 검사에서도 자동 통과).
     *  - 일반 회원은 "없음 (일반 회원 — 본인 자료만 조회 가능)".
     */
    private static String permissionsLabelFor(MemberSession ms) {
        if (ms == null) return "없음";
        if (ms.getRole() == com.team.intranet.enums.member.Role.ADMIN
            || ms.getRole() == com.team.intranet.enums.member.Role.MASTER) {
            return "전체 (회사 내 모든 데이터 조회 가능)";
        }
        java.util.Set<com.team.intranet.enums.member.SubAdminPermission> perms = ms.getPermissions();
        if (perms == null || perms.isEmpty()) {
            return "없음 (일반 회원 — 본인 자료만 조회 가능)";
        }
        StringBuilder sb = new StringBuilder();
        for (com.team.intranet.enums.member.SubAdminPermission p : perms) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(permissionLabelKo(p));
        }
        return sb.toString();
    }

    /** SubAdminPermission enum → 시스템 프롬프트에 적합한 한국어 한 단어. 모르는 값은 enum 이름 그대로. */
    private static String permissionLabelKo(com.team.intranet.enums.member.SubAdminPermission p) {
        if (p == null) return "";
        // enum name 별 한국어 매핑. 누락된 enum 은 name() 그대로 노출.
        return switch (p.name()) {
            case "MEMBER_MANAGEMENT"     -> "회원 관리";
            case "ATTENDANCE_MANAGEMENT" -> "근태 관리";
            case "BOARD_MANAGEMENT"      -> "게시판 관리";
            case "DEPT_MANAGEMENT"       -> "부서 관리";
            case "POSITION_MANAGEMENT"   -> "직급 관리";
            case "TRASH_MANAGEMENT"      -> "휴지통 관리";
            case "APPROVAL_MANAGEMENT"   -> "결재 양식 관리";
            default                      -> p.name();
        };
    }
}
