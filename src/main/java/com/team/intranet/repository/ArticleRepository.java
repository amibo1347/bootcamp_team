package com.team.intranet.repository;

import com.team.intranet.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.team.intranet.entity.Board;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    List<Article> findByBoard_BoardIdAndIsDeletedFalseOrderByCreatedAtDesc(Long boardId);

     @Query("""
      SELECT a FROM Article a
      JOIN FETCH a.board
      LEFT JOIN FETCH a.author
      WHERE a.board.boardId = :boardId AND a.isDeleted = false
  """)
    Page<Article> findByBoard_BoardIdAndIsDeletedFalse(Long boardId, Pageable pageable);

    Optional<Article> findByArticleIdAndBoard_BoardIdAndIsDeletedFalse(Long articleId, Long boardId);
}
