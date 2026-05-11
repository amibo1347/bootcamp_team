package com.team.intranet.dto;

import com.team.intranet.entity.Article;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleUnifiedTrashDto {

    private Long articleId;
    private String title;
    private Long boardId;
    /** 회사 단위 집계 화면용 게시판 표시 이름 */
    private String boardName;
    private String authorName;
    private String createdAt;

    public static ArticleUnifiedTrashDto from(Article article) {
        ArticleDto base = ArticleDto.from(article);
        ArticleUnifiedTrashDto dto = new ArticleUnifiedTrashDto();
        dto.setArticleId(base.getArticleId());
        dto.setTitle(base.getTitle());
        dto.setBoardId(base.getBoardId());
        dto.setBoardName(article.getBoard() != null ? article.getBoard().getBoardName() : null);
        dto.setAuthorName(base.getAuthorName());
        dto.setCreatedAt(base.getCreatedAt());
        return dto;
    }
}
