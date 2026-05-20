package com.team.intranet.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.Article;
import com.team.intranet.enums.board.BoardType;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    List<Article> findByBoard_BoardIdAndIsDeletedFalseOrderByCreatedAtDesc(Long boardId);

     @Query("""
      SELECT a FROM Article a
      JOIN FETCH a.board
      LEFT JOIN FETCH a.author
      WHERE a.board.boardId = :boardId AND a.isDeleted = false
  """)
    Page<Article> findByBoard_BoardIdAndIsDeletedFalse(Long boardId, Pageable pageable);

     @Query("""
      SELECT a FROM Article a
      JOIN FETCH a.board
      LEFT JOIN FETCH a.author
      WHERE a.board.boardId = :boardId AND a.isDeleted = true
  """)
  Page<Article> findByBoard_BoardIdAndIsDeletedTrue(Long boardId, Pageable pageable);

    Page<Article> findByBoard_BoardIdAndAuthor_MemberIdAndIsDeletedTrue(Long boardId, Long memberId, Pageable pageable);

    Optional<Article> findByArticleIdAndBoard_BoardIdAndIsDeletedFalse(Long articleId, Long boardId);

    Optional<Article> findByArticleIdAndBoard_BoardIdAndIsDeletedTrue(Long articleId, Long boardId);

    List<Article> findByIsDeletedTrueAndBoard_BoardTypeAndDeletedAtBefore(
        BoardType boardType, LocalDateTime threshoId);
    
    /** 회사(테넌트) 단위 소프트 삭제 글 페이지 — board·author 패치 포함 */
    @Query("""
        SELECT a FROM Article a
        JOIN FETCH a.board b
        LEFT JOIN FETCH a.author
        WHERE b.company.companyId = :companyId AND a.isDeleted = true
        """)
    Page<Article> findDeletedByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    /**
     * 회사 단위 + 작성자(=본인) 소프트 삭제 글 페이지.
     * ※ 통합 휴지통에서 TRASH_MANAGEMENT 권한이 없는 일반 사용자에게 본인 작성 글만 보여주기 위한 쿼리.
     */
    @Query("""
        SELECT a FROM Article a
        JOIN FETCH a.board b
        LEFT JOIN FETCH a.author
        WHERE b.company.companyId = :companyId
          AND a.author.memberId = :memberId
          AND a.isDeleted = true
        """)
    Page<Article> findDeletedByCompanyIdAndAuthorId(
        @Param("companyId") Long companyId,
        @Param("memberId") Long memberId,
        Pageable pageable);

    /** 회원이 종료 상태(LEAVE/BANNED)로 전이될 때, 작성한 모든 글의 표시명을 고정. */
    @Modifying
    @Query("UPDATE Article a SET a.authorDisplayName = :name WHERE a.author.memberId = :memberId AND a.authorDisplayName IS NULL")
    int markAuthorDisplayName(@Param("memberId") Long memberId, @Param("name") String name);

    /** 회사별 게시글 수 (삭제 제외) — MASTER 사용량 대시보드. */
    @Query("SELECT COUNT(a) FROM Article a WHERE a.board.company.companyId = :companyId AND a.isDeleted = false")
    long countByCompanyId(@Param("companyId") Long companyId);

}
