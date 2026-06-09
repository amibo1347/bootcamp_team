package com.team.intranet.repository;

import com.team.intranet.entity.Comment;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 게시글의 최상위 댓글(parent IS NULL) 작성순
    List<Comment> findByArticle_ArticleIdAndParentIsNull(Long articleId, Sort sort);

    // 특정 부모 댓글에 달린 대댓글 작성순
    List<Comment> findByParent_CommentIdOrderByCreatedAtAsc(Long parentCommentId);

    // 게시글의 전체 댓글 수 (Article.commentCount 동기화용)
    long countByArticle_ArticleId(Long articleId);

    // 회원이 종료 상태(LEAVE/BANNED)로 전이될 때, 작성한 모든 댓글의 표시명을 고정
    @Modifying
    @Query("UPDATE Comment c SET c.authorDisplayName = :name WHERE c.author.memberId = :memberId AND c.authorDisplayName IS NULL")
    int markAuthorDisplayName(@Param("memberId") Long memberId, @Param("name") String name);
}
