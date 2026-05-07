package com.team.intranet.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;

@Entity
@Table(name = "tbl_article")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

// 게시글 엔티티 
public class Article {

    // 변수

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board; // 게시판과의 연관관계

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member author; // 작성자와의 연관관계

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company; // 회사 정보 

    @Column(name = "is_anonymous")
    private boolean isAnonymous; // 익명 여부

    @Column(name = "view_count")
    private int viewCount; // 조회수

    @Column(name = "comment_count")
    private int commentCount; // 댓글 수

    @Column(name = "created_at")
    private String createdAt; // 생성일

    @Column(name = "updated_at")
    private String updatedAt; // 수정일

    @Column(name = "is_deleted")
    private boolean isDeleted; // 삭제 여부

    @Column(name = "attachment_url")
    private String attachmentUrl; // 첨부파일 URL
}
