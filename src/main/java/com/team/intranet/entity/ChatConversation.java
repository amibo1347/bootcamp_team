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
    }

    /** ms 입장에서의 상대방. */
    public Member otherSide(Long memberId) {
        if (peerA != null && peerA.getMemberId().equals(memberId)) return peerB;
        return peerA;
    }
}
