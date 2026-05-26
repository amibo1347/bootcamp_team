package com.team.intranet.repository;

import com.team.intranet.entity.ArticleAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<ArticleAttachment, Long>{
     List<ArticleAttachment> findByArticle_ArticleId(Long articleId);

     /** 보존기간 만료 글 일괄 삭제용 — bulk delete 한 방. ArticleRetentionScheduler 의 N+1 회피. */
     @Modifying
     @Query("DELETE FROM ArticleAttachment a WHERE a.article.articleId IN :ids")
     void deleteByArticleIdIn(@Param("ids") Collection<Long> ids);
}
