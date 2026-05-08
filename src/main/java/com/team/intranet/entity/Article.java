package com.team.intranet.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;

import com.team.intranet.dto.ArticleDto;

@Entity
@Table(name = "tbl_article")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// 게시글 엔티티 
public class Article {

    // 변수

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "title")
    private String title;

    @Lob
    @Column(name = "CONTENT", columnDefinition = "CLOB", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board; // 게시판과의 연관관계

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member author; // 작성자와의 연관관계

    @Column(name = "is_anonymous")
    private boolean isAnonymous; // 익명 여부

    @Column(name = "view_count")
    private Long viewCount; // 조회수

    @Column(name = "comment_count")
    private int commentCount; // 댓글 수

    @Column(name = "created_at")
    private LocalDateTime createdAt; // 생성일

    @Column(name = "updated_at")
    private String updatedAt; // 수정일

    @Column(name = "is_deleted")
    private boolean isDeleted; // 삭제 여부

    @Column(name = "attachment_url")
    private String attachmentUrl; // 첨부파일 URL

    public static Article create(Board board, Member author, ArticleDto dto, boolean isAnonymous) {
        return Article.builder()
            .title(dto.getTitle())
            .content(dto.getContent())
            .board(board)
            .author(author)
            .isAnonymous(isAnonymous)
            .viewCount(0L)
            .createdAt(LocalDateTime.now())
            .isDeleted(false)
            .build();
    }

    

    public void increaseViewCount() {
        this.viewCount++;
    }

    public void delete() {
        this.isDeleted = true;
    }

    public boolean isAuthor(Long memberId) {
        return this.author.getMemberId().equals(memberId);
    }
}
