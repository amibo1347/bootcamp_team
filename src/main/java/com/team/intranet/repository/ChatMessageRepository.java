package com.team.intranet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 대화방 메시지 (시간 오름차순). 첨부는 별도 매퍼에서 로드 — N+1 회피하려면 추후 batch fetch. */
    @Query("""
        SELECT m FROM ChatMessage m
        JOIN FETCH m.sender s
        WHERE m.conversation.conversationId = :conversationId
        ORDER BY m.createdAt ASC, m.messageId ASC
        """)
    List<ChatMessage> findByConversationIdAsc(@Param("conversationId") Long conversationId);

    /** 대화방 최신 메시지 1건 (목록 미리보기용). */
    Optional<ChatMessage> findFirstByConversation_ConversationIdOrderByCreatedAtDescMessageIdDesc(Long conversationId);
}
