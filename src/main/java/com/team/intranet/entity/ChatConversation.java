package com.team.intranet.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 1:1 채팅 대화방.
 * ※ peerA / peerB 는 member_id 가 작은 쪽을 peerA 로 정규화해 저장 → 같은 두 사람은 항상 동일 row.
 * ※ 회사 격리: 같은 회사 멤버끼리만 대화 가능 — service 가 검증.
 */
@Entity
@Table(
    name = "tbl_chat_conversation",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_chat_conv_pair",
        columnNames = {"company_id", "peer_a_id", "peer_b_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conversation_id")
    private Long conversationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "peer_a_id", nullable = false)
    private Member peerA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "peer_b_id", nullable = false)
    private Member peerB;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ─── per-user 설정 ───────────────────────────────────────────────
    //  ※ 1:1 채팅은 양쪽이 같은 row 를 공유하지만 [고정 / 제목 변경 / 나가기] 는 본인 시점만 적용되어야 한다.
    //    별도 테이블 분리 대신 peerA/B 컬럼을 한 row 에 직접 두어 단순화.
    //    helper 메서드로 memberId 가 어느 쪽인지에 따라 자동 분기.

    /** peerA / peerB 가 각자 고정한 시점. null 이면 미고정. */
    @Column(name = "pinned_at_a")
    private LocalDateTime pinnedAtA;

    @Column(name = "pinned_at_b")
    private LocalDateTime pinnedAtB;

    /** peerA / peerB 가 각자 지정한 커스텀 제목. null 이면 상대 이름을 기본 표시. */
    @Column(name = "custom_title_a", length = 100)
    private String customTitleA;

    @Column(name = "custom_title_b", length = 100)
    private String customTitleB;

    /** peerA / peerB 가 각자 "나가기" 한 시점. null 이면 미숨김. 양쪽 다 hidden 이면 cascade delete. */
    @Column(name = "hidden_at_a")
    private LocalDateTime hiddenAtA;

    @Column(name = "hidden_at_b")
    private LocalDateTime hiddenAtB;

    /** ms 가 이 대화방의 참여자인지 — 같은 회사 + (peerA == ms || peerB == ms). */
    public boolean involves(Long memberId) {
        return (peerA != null && peerA.getMemberId().equals(memberId))
            || (peerB != null && peerB.getMemberId().equals(memberId));
    }

    /** 두 회원으로 정규화된 conversation 만들기 (peerA.id < peerB.id). */
    public static ChatConversation createNormalized(Company company, Member m1, Member m2) {
        Member a, b;
        if (m1.getMemberId() <= m2.getMemberId()) { a = m1; b = m2; } else { a = m2; b = m1; }
        return ChatConversation.builder()
            .company(company)
            .peerA(a)
            .peerB(b)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
        // 누군가 메시지를 보내면 양쪽 hidden 자동 해제 (카톡 패턴 — 새 메시지로 다시 나타남)
        this.hiddenAtA = null;
        this.hiddenAtB = null;
    }

    /** ms 입장에서의 상대방. */
    public Member otherSide(Long memberId) {
        if (peerA != null && peerA.getMemberId().equals(memberId)) return peerB;
        return peerA;
    }

    // ─── per-user 헬퍼 ───────────────────────────────────────────────

    /** memberId 가 peerA 인지 (true) peerB 인지 (false). */
    public boolean isSideA(Long memberId) {
        return peerA != null && peerA.getMemberId().equals(memberId);
    }

    public LocalDateTime pinnedAtFor(Long memberId) {
        return isSideA(memberId) ? pinnedAtA : pinnedAtB;
    }

    public boolean isPinnedBy(Long memberId) {
        return pinnedAtFor(memberId) != null;
    }

    /** 본인 시점 고정 토글. */
    public void togglePin(Long memberId) {
        LocalDateTime now = (pinnedAtFor(memberId) == null) ? LocalDateTime.now() : null;
        if (isSideA(memberId)) this.pinnedAtA = now; else this.pinnedAtB = now;
    }

    public String customTitleFor(Long memberId) {
        return isSideA(memberId) ? customTitleA : customTitleB;
    }

    /** 본인 시점 커스텀 제목 설정. null/blank → 기본(상대 이름)으로 복귀. */
    public void setCustomTitleFor(Long memberId, String title) {
        String normalized = (title == null || title.isBlank()) ? null : title.trim();
        if (isSideA(memberId)) this.customTitleA = normalized; else this.customTitleB = normalized;
    }

    public boolean isHiddenBy(Long memberId) {
        return isSideA(memberId) ? hiddenAtA != null : hiddenAtB != null;
    }

    /** 본인 시점 "나가기" — 본인 hidden 마크. 양쪽 모두 hidden 이면 호출자가 cascade delete 수행. */
    public void hide(Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        if (isSideA(memberId)) this.hiddenAtA = now; else this.hiddenAtB = now;
    }

    public boolean isHiddenByBoth() {
        return hiddenAtA != null && hiddenAtB != null;
    }
}
