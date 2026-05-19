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
import com.team.intranet.dto.ai.LlmMessage;
import com.team.intranet.dto.ai.LlmResponse;
import com.team.intranet.entity.AiChatMessage;
import com.team.intranet.entity.AiChatSession;
import com.team.intranet.entity.AiConfig;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.AiChatRole;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.AiChatMessageRepository;
import com.team.intranet.repository.AiChatSessionRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 비서 도메인 서비스 — 세션 관리 + LLM 호출.
 *  - 세션 = 회원 1명 소유. 다른 회원은 접근 불가.
 *  - 메시지 = USER/ASSISTANT 교대로 저장.
 *  - 호출 시 최근 N개 메시지를 LLM context 로 전달 (토큰 한계 대응).
 *  - 회원당 일일 USER 메시지 수 제한 = AiConfig.rateLimitPerDay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    /** LLM 호출 시 전달할 최근 메시지 수 (USER+ASSISTANT 합산). */
    private static final int HISTORY_LIMIT = 20;
    /** 세션 제목 자동 갱신 시 첫 USER 메시지에서 잘라낼 길이. */
    private static final int TITLE_MAX_LEN = 24;

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
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private static final DateTimeFormatter HUMAN_FMT = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm");
    private static final DateTimeFormatter HUMAN_TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm");

    // ─── 세션 ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AiChatSessionDto> findMySessions(MemberSession ms) {
        Member me = loadMember(ms);
        List<AiChatSession> sessions = sessionRepository.findByMemberOrderByUpdatedAtDesc(me);
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
        List<LlmMessage> llmMessages = buildLlmMessages(session, me);

        // 4. LLM 호출
        LlmResponse resp;
        try {
            resp = llmClientFactory.generate(llmMessages);
        } catch (Exception e) {
            log.warn("[AI-CHAT] LLM 호출 실패 sessionId={} : {}", sessionId, e.toString());
            // 429 (quota 초과) 는 별도 ErrorCode 로 친절한 안내
            if (e.getMessage() != null && e.getMessage().contains("HTTP 429")) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_QUOTA_EXCEEDED);
            }
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }

        // 5. ASSISTANT 응답에서 액션 제안 (일정/결재 JSON) 분리
        String rawContent = resp.content() != null ? resp.content() : "";
        AiProposalExtractor.Extracted ex = proposalExtractor.extract(rawContent);

        AiChatMessage assistantMsg = AiChatMessage.assistant(session,
            ex.cleanedContent() != null ? ex.cleanedContent() : "",
            resp.promptTokens(), resp.completionTokens());
        if (ex.proposalJson() != null) {
            assistantMsg.setProposalJson(ex.proposalJson());
            assistantMsg.setProposalApplied(false);
        }
        assistantMsg = messageRepository.save(assistantMsg);

        // 6. 세션 갱신 시각 touch
        session.touch();

        log.info("[AI-CHAT] sessionId={} userMsgId={} assistantMsgId={} promptTokens={} completionTokens={}",
            sessionId, userMsg.getMessageId(), assistantMsg.getMessageId(),
            resp.promptTokens(), resp.completionTokens());

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
            log.warn("[AI-CHAT] proposal JSON 파싱 실패 messageId={} json={}", messageId, msg.getProposalJson(), e);
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
            log.error("[AI-CHAT] apply 실패 messageId={} type={} calendarId={} attendees={} proposalJson={}",
                messageId, type, p.calendarId(), p.attendeeNames(), msg.getProposalJson(), e);
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

        com.team.intranet.entity.Calendar created = calendarService.createCalendar(ms, cdto);
        log.info("[AI-CHAT] calendar created id={} sharedWith={}", created.getCalendarId(), share.matchedNames);
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
        log.info("[AI-CHAT] applyUpdate calendarId={} title={} start={} end={} attendees={} matched={} memberIds={}",
            p.calendarId(), cdto.getTitle(), startAt, endAt,
            p.attendeeNames(), share.matchedNames, share.memberIds);

        if (!share.memberIds.isEmpty()) {
            cdto.setVisibility(com.team.intranet.enums.Visibility.SPECIFIC);
            cdto.setShareMemberIds(share.memberIds);
        }

        calendarService.updateCalendar(ms, p.calendarId(), cdto);
        log.info("[AI-CHAT] calendar updated id={} sharedWith={}", p.calendarId(), share.matchedNames);
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
        log.info("[AI-CHAT] calendar deleted id={}", p.calendarId());

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
     */
    private List<LlmMessage> buildLlmMessages(AiChatSession session, Member me) {
        List<LlmMessage> out = new ArrayList<>();
        out.add(LlmMessage.system(buildSystemPrompt(me)));

        List<AiChatMessage> recent = messageRepository
            .findBySession_SessionIdOrderByCreatedAtDescMessageIdDesc(
                session.getSessionId(), PageRequest.of(0, HISTORY_LIMIT));
        Collections.reverse(recent);
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

    private String buildSystemPrompt(Member me) {
        String position    = me.getPosition() != null ? me.getPosition().getPositionName() : "직원";
        String dept        = me.getDept()     != null ? me.getDept().getDeptName()        : "미지정";
        String companyName = me.getCompany()  != null ? me.getCompany().getCompanyName()  : "(미지정)";
        Long companyId     = me.getCompany()  != null ? me.getCompany().getCompanyId()    : null;

        String basePrompt = """
            당신은 한국 회사 인트라넷의 AI 비서입니다.
            - 한국어로 정중하고 간결하게 답변하세요.
            - 사용자 정보: 이름=%s, 회사=%s, 직급=%s, 부서=%s
            - 오늘 날짜: %s
            - 모르는 내용은 추측하지 말고 모른다고 답하세요.

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
                LocalDate.now().toString(),
                companyName);

        // 회사의 AI 활성 게시판 + 본인 일정을 컨텍스트로 첨부.
        String boardContext    = companyId != null ? aiBoardContextService.buildContext(companyId) : "";
        String calendarContext = aiCalendarContextService.buildContext(me.getMemberId());
        return basePrompt + boardContext + calendarContext;
    }

    private static String truncate(String s, int n) {
        if (s == null) return "새 대화";
        String oneLine = s.replaceAll("\\s+", " ").trim();
        if (oneLine.isEmpty()) return "새 대화";
        return oneLine.length() <= n ? oneLine : oneLine.substring(0, n) + "…";
    }
}
