package com.team.intranet.service.ai;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }

        // 5. ASSISTANT 메시지 저장
        AiChatMessage assistantMsg = messageRepository.save(
            AiChatMessage.assistant(session,
                resp.content() != null ? resp.content() : "",
                resp.promptTokens(), resp.completionTokens()));

        // 6. 세션 갱신 시각 touch
        session.touch();

        log.info("[AI-CHAT] sessionId={} userMsgId={} assistantMsgId={} promptTokens={} completionTokens={}",
            sessionId, userMsg.getMessageId(), assistantMsg.getMessageId(),
            resp.promptTokens(), resp.completionTokens());

        return AiChatMessageDto.from(assistantMsg);
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
                case ASSISTANT -> out.add(LlmMessage.assistant(content));
                case SYSTEM    -> { /* 저장된 SYSTEM 은 무시 — 위에서 한 번만 주입 */ }
            }
        }
        return out;
    }

    private String buildSystemPrompt(Member me) {
        String position    = me.getPosition() != null ? me.getPosition().getPositionName() : "직원";
        String dept        = me.getDept()     != null ? me.getDept().getDeptName()        : "미지정";
        String companyName = me.getCompany()  != null ? me.getCompany().getCompanyName()  : "(미지정)";
        return """
            당신은 한국 회사 인트라넷의 AI 비서입니다.
            - 한국어로 정중하고 간결하게 답변하세요.
            - 사용자 정보: 이름=%s, 회사=%s, 직급=%s, 부서=%s
            - 오늘 날짜: %s
            - 모르는 내용은 추측하지 말고 모른다고 답하세요.

            ⚠️ 회사 격리 원칙 (절대 위반 금지):
            - 답변에 사용 가능한 데이터는 위 사용자 회사("%s") 범위 내로 제한됩니다.
            - 다른 회사의 게시글/일정/회원 정보는 절대 참조하지 마세요.
            - 향후 검색 도구(tool)가 제공될 때도 이 회사 ID 범위로만 호출하세요.
            """.formatted(
                me.getName(), companyName, position, dept,
                LocalDate.now().toString(),
                companyName);
    }

    private static String truncate(String s, int n) {
        if (s == null) return "새 대화";
        String oneLine = s.replaceAll("\\s+", " ").trim();
        if (oneLine.isEmpty()) return "새 대화";
        return oneLine.length() <= n ? oneLine : oneLine.substring(0, n) + "…";
    }
}
