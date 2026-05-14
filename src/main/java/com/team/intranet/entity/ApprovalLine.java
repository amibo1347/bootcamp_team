package com.team.intranet.entity;

import java.time.LocalDateTime;

import com.team.intranet.enums.ApprovalStatus;

import jakarta.persistence.*;
import lombok.*;

/**
 * 결재선 (1 Approval : N ApprovalLine).
 * 한 결재 문서는 단계(level 1~4)별로 ApprovalLine 을 가진다.
 * 마지막 level 결재자가 SUB_ADMIN+ 이어야 한다(서비스에서 검증).
 */
@Entity
@Table(name = "tbl_approval_line",
        uniqueConstraints = @UniqueConstraint(columnNames = {"approval_id", "level_no"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ApprovalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_line_id")
    private Long approvalLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_id", nullable = false)
    private Approval approval;

    // 결재 단계 (1~4). 마지막 단계가 최종 승인자.
    @Column(name = "level_no", nullable = false)
    private Integer level;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_id", nullable = false)
    private Member approver;

    // 이 단계 결재자의 처리 결과. PENDING(미처리) / APPROVED / REJECTED / ON_HOLD.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalStatus status;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "line_comment", length = 500)
    private String comment;
}
