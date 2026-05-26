package com.team.intranet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_article_attachment")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ArticleAttachment {
    
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

      /** 글 작성 중에는 null, 글 저장 시 연결됨 */
      @ManyToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "article_id")
      private Article article;

      @ManyToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "uploader_id")
      private Member uploader;

      @ManyToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "company_id")
      private Company company;

      @Column(name = "created_at")
      private LocalDateTime createdAt;
}
