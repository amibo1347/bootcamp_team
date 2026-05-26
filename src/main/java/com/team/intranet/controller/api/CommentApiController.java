package com.team.intranet.controller.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.dto.CommentDto;
import com.team.intranet.session.MemberSession;
import com.team.intranet.service.CommentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/board/{boardId}/articles/{articleId}/comments")
@RequiredArgsConstructor
public class CommentApiController {

    private final CommentService commentService;

    /** 댓글/대댓글 작성. dto.parentCommentId 가 있으면 대댓글. */
    @PostMapping("/new")
    public ResponseEntity<Void> createComment(
            @PathVariable Long boardId,
            @PathVariable Long articleId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @ModelAttribute CommentDto dto) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        dto.setArticleId(articleId);
        commentService.createComment(ms, dto);
        return ResponseEntity.ok().build();
    }

    /** 게시글의 댓글 목록. 평면 리스트 [top1, top1.reply..., top2, top2.reply...]. */
    @GetMapping("")
    public ResponseEntity<List<CommentDto>> listComments(
            @PathVariable Long boardId,
            @PathVariable Long articleId,
            @RequestParam(value = "sort", defaultValue = "asc") String sort,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(commentService.getCommentsByArticle(ms, articleId, sort));
    }

    /** 댓글 수정 — 작성자 본인만. */
    @PostMapping("/{commentId}/edit")
    public ResponseEntity<Void> updateComment(
            @PathVariable Long boardId,
            @PathVariable Long articleId,
            @PathVariable Long commentId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @ModelAttribute CommentDto dto) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        commentService.updateComment(ms, articleId, commentId, dto);
        return ResponseEntity.ok().build();
    }

    /** 댓글 삭제 — 작성자 본인 또는 관리자. 최상위 댓글이면 대댓글까지 CASCADE 로 함께 삭제. */
    @PostMapping("/{commentId}/delete")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long boardId,
            @PathVariable Long articleId,
            @PathVariable Long commentId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        commentService.deleteComment(ms, articleId, commentId);
        return ResponseEntity.noContent().build();
    }
}
