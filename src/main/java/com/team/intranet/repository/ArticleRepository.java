package com.team.intranet.repository;

import com.team.intranet.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    List<Article> findByBoard_BoardIdAndIsDeletedFalseOrderByCreatedAtDesc(Long boardId);
}
