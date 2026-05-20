package com.team.intranet.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team.intranet.event.ChatMessageSentEvent;

import lombok.RequiredArgsConstructor;

/**
 * 채팅 메시지 DB 커밋 이후에만 SSE 를 발행한다.
 * (트랜잭션 안에서 바로 push 하면 일부 환경에서 수신·직렬화가 불안정할 수 있음)
 */
@Component
@RequiredArgsConstructor
public class ChatSseListener {

    private final ChatSseService chatSseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessageSent(ChatMessageSentEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", event.conversationId());
        payload.put("message", event.message());
        chatSseService.publishMessage(event.toMemberId(), payload);
    }
}
