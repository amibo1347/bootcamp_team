package com.team.intranet.repository;

import com.team.intranet.entity.ArticleAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<ArticleAttachment, Long>{
     List<ArticleAttachment> findByArticle_ArticleId(Long articleId);
}
