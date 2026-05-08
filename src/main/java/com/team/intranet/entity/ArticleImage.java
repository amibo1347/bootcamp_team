package com.team.intranet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.Company;


@Entity
@Table(name="tbl_article_image")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticleImage {
    

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", nullable = false)
    private byte[] data;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member uploader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

}
