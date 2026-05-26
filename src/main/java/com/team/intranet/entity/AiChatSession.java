package com.team.intranet.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 비서 대화 세션. 회원당 여러 세션 보유 (메신저 +버튼으로 추가).
 *  - title 은 처음 "새 대화", 첫 메시지 후 자동 갱신.
 *  - 메시지는 AiChatMessage 에 별도 row. 세션 삭제 시 cascade (Repository delete 쿼리에서 처리).
 */
@Entity
@Table(name = "tbl_ai_chat_session",
    indexes = {
        @Index(name = "idx_ai_session_member_updated", columnList = "member_id, updated_at"),
        @Index(name = "idx_ai_session_company", columnList = "company_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 회사 격리 — AI 가 접근 가능한 컨텍스트(게시판/일정/회원 등)는 이 회사 범위 내로 강제.
     *  - 회원이 회사를 옮겨도 과거 세션의 회사 컨텍스트가 보존됨 (감사용).
     *  - 모든 검색 tool 은 이 companyId 로 격리해야 함 (필수 원칙).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** 세션 제목. 첫 메시지 앞 N자로 자동 갱신, 이후 사용자가 [제목 수정] 메뉴로 변경 가능. */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 고정한 시점. null 이면 미고정.
     *  - 목록 정렬: 고정 세션이 항상 위 (제목 가나다순), 미고정은 updatedAt 최신순.
     *  - 사용자가 [고정하기] / [고정 해제] 토글.
     */
    @Column(name = "pinned_at")
    private LocalDateTime pinnedAt;

    public static AiChatSession createNew(Member member) {
        LocalDateTime now = LocalDateTime.now();
        return AiChatSession.builder()
            .member(member)
            .company(member.getCompany())
            .title("새 대화")
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 사용자 직접 제목 변경. 빈 값 거부는 호출자(서비스)가 책임. */
    public void rename(String newTitle) {
        this.title = newTitle;
    }

    /** 고정/고정 해제 토글. 고정 상태면 해제, 아니면 현재 시각으로 고정. */
    public void togglePin() {
        this.pinnedAt = (this.pinnedAt == null) ? LocalDateTime.now() : null;
    }

    public boolean isPinned() {
        return this.pinnedAt != null;
    }
}
