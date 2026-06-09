package com.team.intranet.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.context.ApplicationEventPublisher;

import com.team.intranet.dto.ChatConversationDto;
import com.team.intranet.dto.ChatMessageDto;
import com.team.intranet.dto.ChatPeerDto;
import com.team.intranet.event.ChatMessageSentEvent;
import com.team.intranet.entity.ChatAttachment;
import com.team.intranet.entity.ChatConversation;
import com.team.intranet.entity.ChatMessage;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.member.Status;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.ChatAttachmentRepository;
import com.team.intranet.repository.ChatConversationRepository;
import com.team.intranet.repository.ChatMessageRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 채팅 도메인 서비스.
 *  - 1:1 대화방 정규화 생성/조회
 *  - 메시지 (텍스트 + 파일) 작성
 *  - 회원 검색 (새 채팅 화면용)
 *  - 회사 격리 / 본인 참여 검증
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatAttachmentRepository attachmentRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AlertService alertService;

    // ─── 회원 검색 (새 채팅 화면) ─────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChatPeerDto> searchPeers(MemberSession ms) {
        // 활성 회원만, 본인 제외. 부서/직급/이름 검색은 클라이언트 사이드 필터.
        return memberRepository.findByStatusAndCompanyCompanyId(Status.JOIN, ms.getCompanyId())
            .stream()
            .filter(m -> !m.getMemberId().equals(ms.getMemberId()))
            .sorted(Comparator.comparing(Member::getName, Comparator.nullsLast(String::compareTo)))
            .map(ChatPeerDto::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public byte[] getMemberProfileImage(MemberSession ms, Long memberId) {
        Member m = memberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (!m.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return m.getProfileImg();
    }

    // ─── 대화방 ─────────────────────────────────────────────────────

    /** 상대(peerId) 와의 1:1 대화방 — 없으면 생성. */
    @Transactional
    public ChatConversationDto getOrCreateConversation(MemberSession ms, Long peerId) {
        if (peerId == null || peerId.equals(ms.getMemberId())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        Member me = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Member peer = memberRepository.findById(peerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (!peer.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        Long aId = Math.min(me.getMemberId(), peer.getMemberId());
        Long bId = Math.max(me.getMemberId(), peer.getMemberId());

        ChatConversation conv = conversationRepository
            .findByCompany_CompanyIdAndPeerA_MemberIdAndPeerB_MemberId(ms.getCompanyId(), aId, bId)
            .orElseGet(() -> conversationRepository.save(
                ChatConversation.createNormalized(me.getCompany(), me, peer)
            ));

        ChatMessage last = messageRepository
            .findFirstByConversation_ConversationIdOrderByCreatedAtDescMessageIdDesc(conv.getConversationId())
            .orElse(null);
        return ChatConversationDto.from(conv, ms.getMemberId(), last);
    }

    /**
     * 내 대화방 목록.
     *  - 본인이 [나가기] 한 방(hiddenAt 세팅)은 목록에서 제외 (새 메시지 도착 시 touch() 가 자동 해제).
     *  - 정렬: 본인 시점 고정 방이 항상 위 → 고정끼리는 displayTitle(=customTitle ?: 상대 이름) 가나다순,
     *         비고정끼리는 Repository 기본(최신순) 유지.
     */
    @Transactional(readOnly = true)
    public List<ChatConversationDto> findMyConversations(MemberSession ms) {
        List<ChatConversation> convs = conversationRepository.findMineByCompany(ms.getCompanyId(), ms.getMemberId());
        Long meId = ms.getMemberId();

        List<ChatConversationDto> dtos = new ArrayList<>(convs.size());
        for (ChatConversation conv : convs) {
            if (conv.isHiddenBy(meId)) continue; // 본인 시점 숨김
            ChatMessage last = messageRepository
                .findFirstByConversation_ConversationIdOrderByCreatedAtDescMessageIdDesc(conv.getConversationId())
                .orElse(null);
            long unread = alertService.countMyChatUnreadForConv(ms, conv.getConversationId());
            dtos.add(ChatConversationDto.from(conv, meId, last, unread));
        }

        // 정렬: 고정 우선 + 고정끼리 displayTitle 가나다순.
        dtos.sort((a, b) -> {
            if (a.isPinned() != b.isPinned()) return a.isPinned() ? -1 : 1;
            if (a.isPinned()) {
                String ta = a.getDisplayTitle() == null ? "" : a.getDisplayTitle();
                String tb = b.getDisplayTitle() == null ? "" : b.getDisplayTitle();
                return ta.compareTo(tb);
            }
            return 0; // 비고정 → 기존 최신순 유지
        });
        return dtos;
    }

    /**
     * 대화방 제목 수정 (per-user) — 점 3개 메뉴 [제목 수정].
     *  - 빈 값/null → 기본 제목(상대 이름)으로 복귀.
     *  - 참여자 검증.
     */
    @Transactional
    public ChatConversationDto renameConversation(MemberSession ms, Long conversationId, String newTitle) {
        ChatConversation conv = loadAndAssertParticipant(ms, conversationId);
        conv.setCustomTitleFor(ms.getMemberId(), newTitle);
        ChatMessage last = messageRepository
            .findFirstByConversation_ConversationIdOrderByCreatedAtDescMessageIdDesc(conv.getConversationId())
            .orElse(null);
        long unread = alertService.countMyChatUnreadForConv(ms, conv.getConversationId());
        return ChatConversationDto.from(conv, ms.getMemberId(), last, unread);
    }

    /**
     * 대화방 고정/고정 해제 토글 (per-user) — 점 3개 메뉴 [고정하기 / 고정 해제].
     *  - 참여자 검증.
     *  - 반환: 새 pinned 상태.
     */
    @Transactional
    public boolean togglePinConversation(MemberSession ms, Long conversationId) {
        ChatConversation conv = loadAndAssertParticipant(ms, conversationId);
        conv.togglePin(ms.getMemberId());
        return conv.isPinnedBy(ms.getMemberId());
    }

    /**
     * 대화방 나가기 (per-user) — 점 3개 메뉴 [채팅방 나가기].
     *  - 본인 시점 hidden 처리. 양쪽 모두 hidden 이면 메시지/첨부까지 cascade delete.
     *  - 새 메시지 도착 시 ChatConversation.touch() 가 양쪽 hidden 을 해제 (카톡 패턴).
     */
    @Transactional
    public void leaveConversation(MemberSession ms, Long conversationId) {
        ChatConversation conv = loadAndAssertParticipant(ms, conversationId);
        conv.hide(ms.getMemberId());
        if (conv.isHiddenByBoth()) {
            conversationRepository.delete(conv); // 메시지·첨부 cascade
        }
    }

    /** 채팅방 진입 = 그 대화방의 본인 알림 일괄 삭제 (읽음 처리). */
    @Transactional
    public void markConversationRead(MemberSession ms, Long conversationId) {
        // 참여자 검증만 하고 실제 삭제는 AlertService 위임.
        loadAndAssertParticipant(ms, conversationId);
        alertService.markChatRead(ms, conversationId);
    }

    /** 본인의 채팅 안 읽음 총합 (FAB/헤더 배지 공통 데이터). */
    @Transactional(readOnly = true)
    public long countMyChatUnreadTotal(MemberSession ms) {
        return alertService.countMyChatUnreadTotal(ms);
    }

    // ─── 메시지 ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChatMessageDto> findMessages(MemberSession ms, Long conversationId) {
        ChatConversation conv = loadAndAssertParticipant(ms, conversationId);
        List<ChatMessage> msgs = messageRepository.findByConversationIdAsc(conv.getConversationId());
        if (msgs.isEmpty()) return List.of();

        // 첨부파일 일괄 조회 — N+1 회피.
        List<Long> ids = msgs.stream().map(ChatMessage::getMessageId).toList();
        Map<Long, List<ChatAttachment>> byMsg = ids.stream().collect(Collectors.toMap(
            id -> id,
            id -> attachmentRepository.findByMessage_MessageId(id)
        ));
        List<ChatMessageDto> out = new ArrayList<>(msgs.size());
        for (ChatMessage m : msgs) {
            out.add(ChatMessageDto.from(m, byMsg.getOrDefault(m.getMessageId(), List.of())));
        }
        return out;
    }

    @Transactional
    public ChatMessageDto sendMessage(MemberSession ms, Long conversationId, String text, List<MultipartFile> files) {
        ChatConversation conv = loadAndAssertParticipant(ms, conversationId);
        Member me = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        boolean hasText = text != null && !text.isBlank();
        boolean hasFile = files != null && files.stream().anyMatch(f -> f != null && !f.isEmpty());
        if (!hasText && !hasFile) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        ChatMessage msg = ChatMessage.create(conv, me, hasText ? text.trim() : null);
        messageRepository.save(msg);

        if (hasFile) {
            for (MultipartFile f : files) {
                if (f == null || f.isEmpty()) continue;
                try {
                    ChatAttachment att = ChatAttachment.of(
                        msg,
                        f.getOriginalFilename() != null ? f.getOriginalFilename() : "untitled",
                        f.getContentType(),
                        f.getSize(),
                        f.getBytes()
                    );
                    attachmentRepository.save(att);
                    msg.getAttachments().add(att);
                } catch (IOException e) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT);
                }
            }
        }

        conv.touch();
        ChatMessageDto dto = ChatMessageDto.from(msg, msg.getAttachments());

        // 상대방 SSE 는 커밋 후 ChatSseListener 가 발행 — 발신자는 HTTP 응답으로 직접 받음.
        Member other = conv.otherSide(ms.getMemberId());
        if (other != null) {
            eventPublisher.publishEvent(new ChatMessageSentEvent(
                other.getMemberId(), conv.getConversationId(), dto));
            // 상대방 알림(Alert) 생성 — 같은 트랜잭션. 채팅방 진입 시 일괄 삭제됨.
            alertService.sendChatMessageAlert(
                ms.getMemberId(), other.getMemberId(), conv, dto.getText());
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public ChatAttachment loadAttachment(MemberSession ms, Long attachmentId) {
        ChatAttachment att = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
        // 대화방 참여자 검증.
        loadAndAssertParticipant(ms, att.getMessage().getConversation().getConversationId());
        return att;
    }

    // ─── 내부 ───────────────────────────────────────────────────────

    private ChatConversation loadAndAssertParticipant(MemberSession ms, Long conversationId) {
        ChatConversation conv = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATUS));
        if (!conv.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (!conv.involves(ms.getMemberId())) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        return conv;
    }
}
