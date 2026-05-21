package com.team.intranet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 전자결재 문서 첨부파일. 게시글 첨부(ArticleAttachment) 와 동일한 구조.
 *  - 기안 작성 중에는 approval = null (업로드만 된 상태).
 *  - 결재 제출 시 ApprovalService.submit 이 approval 을 연결한다.
 *  - 첨부는 선택 사항 — 0개여도 결재 제출 가능.
 */
@Entity
@Table(name = "tbl_approval_attachment")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long attachmentId;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", nullable = false)
    private byte[] data;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    /** 기안 작성 중에는 null, 결재 제출 시 연결됨. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_id")
    private Approval approval;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id")
    private Member uploader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
