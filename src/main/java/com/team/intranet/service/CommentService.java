package com.team.intranet.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.team.intranet.repository.CommentRepository;
import com.team.intranet.repository.ArticleRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.entity.Comment;
import com.team.intranet.entity.Article;
import com.team.intranet.entity.Member;
import com.team.intranet.dto.CommentDto;
import com.team.intranet.session.MemberSession;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.enums.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final ArticleRepository articleRepository;
    private final MemberRepository memberRepository;
    private final BoardService boardService;

    @Transactional
    public Comment createComment(MemberSession ms, CommentDto dto) {
        // 1) 게시글 검증 (존재 / 삭제 여부)
        Article article = articleRepository.findById(dto.getArticleId())
            .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
        if (article.isDeleted()) {
            throw new BusinessException(ErrorCode.ALEADY_DELETE_ARTICLE);
        }

        // 2) 게시판 권한 일괄 검증 (회사일치 + 활성 + read/write/comment scope)
        //    BoardScopeRule (부서/직급별 제한) 까지 BoardService 가 처리해 줌
        boardService.getCommentableBoard(ms, article.getBoard().getBoardId());

        // 3) 부모 댓글 검증 (대댓글일 때만)
        Comment parent = null;
        if (dto.getParentCommentId() != null) {
            parent = commentRepository.findById(dto.getParentCommentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
            // 같은 게시글에 속한 부모인지
            if (!parent.getArticle().getArticleId().equals(article.getArticleId())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            // 깊이 제한: 댓글 -> 대댓글 까지만
            if (parent.getParent() != null) {
                throw new BusinessException(ErrorCode.INVALID_COMMENT_DEPTH);
            }
        }

        // 4) 작성자
        Member author = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 5) 저장 + 게시글 댓글 수 동기화
        Comment saved = commentRepository.save(
            Comment.create(article, author, parent, dto.getContent())
        );
        article.setCommentCount(article.getCommentCount() + 1);
        return saved;
    }

    /**
     * 게시글의 댓글 목록 조회.
     * 반환 순서: [top1, top1.reply1, top1.reply2, top2, top2.reply1, ...]
     * 프론트에서 parentCommentId 가 채워져 있으면 들여쓰기/대댓글로 표시.
     */
    public List<CommentDto> getCommentsByArticle(MemberSession ms, Long articleId) {
        Article article = articleRepository.findById(articleId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
        if (article.isDeleted()) {
            throw new BusinessException(ErrorCode.ALEADY_DELETE_ARTICLE);
        }

        // 읽기 권한만 검사 (회사 일치 + 활성 + READ scope + 부서/직급 규칙)
        boardService.getReadableBoard(ms, article.getBoard().getBoardId());

        List<Comment> roots =
            commentRepository.findByArticle_ArticleIdAndParentIsNullOrderByCreatedAtAsc(articleId);

        List<CommentDto> result = new ArrayList<>();
        for (Comment root : roots) {
            result.add(CommentDto.from(root));
            List<Comment> replies =
                commentRepository.findByParent_CommentIdOrderByCreatedAtAsc(root.getCommentId());
            for (Comment reply : replies) {
                result.add(CommentDto.from(reply));
            }
        }
        return result;
    }

    /**
     * 댓글 수정 — 작성자 본인만 가능. 내용만 변경.
     */
    @Transactional
    public Comment updateComment(MemberSession ms, Long articleId, Long commentId, CommentDto dto) {
        Article article = loadActiveArticle(articleId);
        boardService.getReadableBoard(ms, article.getBoard().getBoardId());

        Comment comment = findCommentInArticle(commentId, articleId);
        if (!comment.isAuthor(ms.getMemberId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        comment.updateContent(dto.getContent());
        return comment;
    }

    /**
     * 댓글 삭제 — 작성자 본인 또는 관리자.
     * - 최상위 댓글 삭제 시 대댓글은 DB ON DELETE CASCADE 로 함께 삭제됨
     * - 게시글이 hard delete 될 때(보존기간 만료 스케줄러)도 동일하게 자동 삭제됨
     * - Article.commentCount 는 함께 사라지는 대댓글 수까지 합쳐서 차감
     */
    @Transactional
    public void deleteComment(MemberSession ms, Long articleId, Long commentId) {
        Article article = loadActiveArticle(articleId);
        boardService.getReadableBoard(ms, article.getBoard().getBoardId());

        Comment comment = findCommentInArticle(commentId, articleId);
        if (!comment.isAuthor(ms.getMemberId()) && !ms.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        int totalRemoved = 1;
        if (comment.getParent() == null) {
            // 최상위 댓글: 자식 대댓글까지 CASCADE 로 사라지므로 미리 카운트
            totalRemoved += commentRepository
                .findByParent_CommentIdOrderByCreatedAtAsc(comment.getCommentId()).size();
        }
        article.setCommentCount(Math.max(0, article.getCommentCount() - totalRemoved));
        commentRepository.delete(comment);
    }

    // ===== 헬퍼 =====

    private Article loadActiveArticle(Long articleId) {
        Article article = articleRepository.findById(articleId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
        if (article.isDeleted()) {
            throw new BusinessException(ErrorCode.ALEADY_DELETE_ARTICLE);
        }
        return article;
    }

    private Comment findCommentInArticle(Long commentId, Long articleId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getArticle().getArticleId().equals(articleId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return comment;
    }
}
