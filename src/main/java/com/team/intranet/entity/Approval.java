package com.team.intranet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.team.intranet.enums.ApprovalStatus;

@Entity
@Table(name = "tbl_approval")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_id")
    private Long approvalId;    // 결재 문서 id

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_template_id", nullable = false)
    private FormTemplate formTemplate; // 결재 양식

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drafter_id", nullable = false)
    private Member drafter; // 결재 작성자(신청자)

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_id", nullable = false)
    private Member approver; // 현재 단계 결재자 캐시 (실제 결재선은 ApprovalLine 참고)

    // 결재선 진행 추적용
    @Column(name = "current_level", nullable = false)
    private Integer currentLevel; // 현재 진행 중인 단계 (1~maxLevel)

    @Column(name = "max_level", nullable = false)
    private Integer maxLevel;     // 총 결재 단계 수 (1~4)

    @Column(name = "title", nullable = false, length = 200)
    private String title;   // 결재 문서 제목

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalStatus status;  // 결재 문서 상태(대기중, 승인, 반려)

    @Column(name = "approver_comment", length = 500)
    private String approverComment; // 결재자 의견 (반려 사유 등)

    @Column(name = "drafted_at")
    private LocalDateTime draftedAt;    // 결재 신청 시간

    @Column(name = "processed_at")
    private LocalDateTime processedAt; // 결재자가 승인/반려를 처리한 시간
    
    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

}
