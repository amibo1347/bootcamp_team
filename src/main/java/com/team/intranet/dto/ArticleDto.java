package com.team.intranet.dto;

import org.springframework.web.bind.annotation.ModelAttribute;

import com.team.intranet.entity.Article;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDto {
    private Long articleId; // 게시글 ID
    private String title;   // 게시글 제목
    private String content; // 게시글 내용
    private Long boardId;   // 게시판 ID
    private Long authorId;  // 작성자 ID
    private boolean isAnonymous;    // 익명 여부
    private Long viewCount;  // 조회수
    private int commentCount;   // 댓글 수
    private String createdAt;
    private String authorName;

    public static ArticleDto from(Article article) {
        ArticleDto dto = new ArticleDto();
        dto.setArticleId(article.getArticleId());
        dto.setTitle(article.getTitle());
        dto.setContent(article.getContent());
        dto.setBoardId(article.getBoard() != null ? article.getBoard().getBoardId() : null);
        dto.setAuthorId(article.getAuthor() != null ? article.getAuthor().getMemberId() : null);
        dto.setAnonymous(article.isAnonymous());
        dto.setViewCount(article.getViewCount());
        dto.setCommentCount(article.getCommentCount());
        dto.setCreatedAt(article.getCreatedAt() != null ? article.getCreatedAt().toString() : null);
        dto.setAuthorName(article.isAnonymous()
          ? "익명"
          : (article.getAuthor() != null ? article.getAuthor().getName() : "-"));
        return dto;
    }
}