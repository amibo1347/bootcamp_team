package com.team.intranet.event;

import com.team.intranet.dto.ChatMessageDto;

/**
 * 채팅 메시지 저장·커밋 완료 후 SSE 로 상대에게 전달할 때 사용하는 도메인 이벤트.
 */
public record ChatMessageSentEvent(Long toMemberId, Long conversationId, ChatMessageDto message) {}
