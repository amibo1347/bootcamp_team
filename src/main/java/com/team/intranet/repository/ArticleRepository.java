package com.team.intranet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.Article;

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

    /** 회사(테넌트) 단위 소프트 삭제 글 페이지 — board·author 패치 포함 */
    @Query("""
        SELECT a FROM Article a
        JOIN FETCH a.board b
        LEFT JOIN FETCH a.author
        WHERE b.company.companyId = :companyId AND a.isDeleted = true
        """)
    Page<Article> findDeletedByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

}
