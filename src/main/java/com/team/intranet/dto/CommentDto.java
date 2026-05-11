package com.team.intranet.dto;

import com.team.intranet.entity.Comment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentDto {
    private Long commentId;
    private Long articleId;
    private Long parentCommentId; // 대댓글이면 부모 댓글 ID, 일반 댓글이면 null
    private Long authorId;        // 영구 삭제 회원이면 null
    private String authorName;    // 화면 표시용 (익명/탈퇴 회원/실명)
    private String content;
    private String createdAt;

    public static CommentDto from(Comment comment) {
        CommentDto dto = new CommentDto();
        dto.setCommentId(comment.getCommentId());
        dto.setArticleId(comment.getArticle() != null ? comment.getArticle().getArticleId() : null);
        dto.setParentCommentId(comment.getParent() != null ? comment.getParent().getCommentId() : null);
        dto.setAuthorId(comment.getAuthor() != null ? comment.getAuthor().getMemberId() : null);
        dto.setAuthorName(resolveAuthorName(comment));
        dto.setContent(comment.getContent());
        dto.setCreatedAt(comment.getCreatedAt() != null ? comment.getCreatedAt().toString() : null);
        return dto;
    }

    // 작성자 표시명 결정 우선순위:
    // 1) 익명 게시글의 댓글  2) 탈퇴/해고로 박제된 표시명  3) 살아있는 author 의 이름  4) 폴백
    private static String resolveAuthorName(Comment comment) {
        if (comment.getArticle() != null && comment.getArticle().isAnonymous()) return "익명";
        String fixed = comment.getAuthorDisplayName();
        if (fixed != null && !fixed.isBlank()) return fixed;
        if (comment.getAuthor() != null) return comment.getAuthor().getName();
        return "탈퇴 회원";
    }
}
