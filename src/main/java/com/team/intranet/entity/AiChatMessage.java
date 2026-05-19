package com.team.intranet.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.team.intranet.enums.AiChatRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 대화 메시지 한 건. role = USER / ASSISTANT.
 *  - 세션 cascade delete: 부모(AiChatSession) 삭제 시 OnDelete.CASCADE 로 자동 정리.
 *  - tokens 필드는 통계용 (provider 응답에서 받아오면 채움, 없으면 null).
 */
@Entity
@Table(name = "tbl_ai_chat_message",
    indexes = {
        @Index(name = "idx_ai_msg_session_created", columnList = "session_id, created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AiChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private AiChatRole role;

    /** 메시지 본문 — 모델 응답이 길 수 있어 CLOB. */
    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static AiChatMessage user(AiChatSession session, String content) {
        return AiChatMessage.builder()
            .session(session)
            .role(AiChatRole.USER)
            .content(content)
            .createdAt(LocalDateTime.now())
            .build();
    }

    public static AiChatMessage assistant(AiChatSession session, String content,
                                          Integer promptTokens, Integer completionTokens) {
        return AiChatMessage.builder()
            .session(session)
            .role(AiChatRole.ASSISTANT)
            .content(content)
            .promptTokens(promptTokens)
            .completionTokens(completionTokens)
            .createdAt(LocalDateTime.now())
            .build();
    }
}
