package com.team.intranet.repository;

import com.team.intranet.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    List<Article> findByBoard_BoardIdAndIsDeletedFalseOrderByCreatedAtDesc(Long boardId);
    Page<Article> findByBoard_BoardIdAndIsDeletedFalse(Long boardId, Pageable pageable);
}
