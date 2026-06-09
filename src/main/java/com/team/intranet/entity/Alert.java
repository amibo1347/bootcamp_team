package com.team.intranet.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.Setter;

import com.team.intranet.enums.Preface;

@Entity
@Table(name = "tbl_alert",
    indexes = {
        @Index(name = "idx_alert_recipient_read", columnList = "recipient_id, is_read"),
        @Index(name = "idx_alert_recipient_created", columnList = "recipient_id, created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Long alertId; // 알림 id (PK)

    @Enumerated(EnumType.STRING)
    @Column(name = "preface", nullable = false)
    private Preface preface; // 머리말 — ※ Enum 클래스(Preface) 새로 생성 필요 (예: SCHEDULE/COMMENT/NOTICE 등 알림 유형 구분)

    @Column(name = "title", nullable = false)
    private String title; // 제목

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt; // 보낸 시점

    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // 알림 만료 시점

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Member sender; // 보낸 사람 (시스템 발송이면 null 허용)
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Member recipient; // 받는 사람 (인앱 알림 대상자) — 알림창에 띄울 사람 식별용. 사실상 거의 필수.

    @Lob
    @Column(name = "content")
    private String content; // 알림 본문 (제목 외에 부가 설명이 필요할 때)

    @Column(name = "is_read")
    private boolean isRead; // 읽음 여부 (알림창에서 읽음/안읽음 구분)

    @Column(name = "link")
    private String link; // 클릭 시 이동할 URL (예: "/calendars/123") — 알림 클릭 → 해당 페이지 이동

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Article article; // 연관 엔티티 ID (예: 일정 ID, 게시글 ID 등)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Calendar calendar; // 일정 알림 전용

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Comment comment;    // 댓글 알림

    /**
     * 채팅 대화방 알림 (1:1 채팅의 새 메시지 알림). null 이면 채팅이 아닌 일반 알림.
     *  - 알림창 조회는 chatConversation IS NULL 인 것만 (채팅 배지는 별도 카운트).
     *  - 채팅방 진입 시 그 conv 의 모든 알림 삭제 (읽음 처리).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_conversation_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatConversation chatConversation;

    @PrePersist
    private void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
