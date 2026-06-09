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

     /** 회사별 첨부 누적 바이트 합. MASTER 사용량 대시보드(스토리지 카드). */
     @Query("SELECT COALESCE(SUM(a.fileSize), 0) FROM ArticleAttachment a WHERE a.company.companyId = :companyId")
     long sumFileSizeByCompanyId(@Param("companyId") Long companyId);

     /** 전체 첨부 누적 바이트 합. KPI 카드(스토리지). */
     @Query("SELECT COALESCE(SUM(a.fileSize), 0) FROM ArticleAttachment a")
     long sumAllFileSize();

     /** 회사별 게시판 첨부 바이트 합 일괄. [companyId, sumBytes]. */
     @Query("SELECT a.company.companyId, COALESCE(SUM(a.fileSize), 0) FROM ArticleAttachment a " +
            "WHERE a.company.companyId IS NOT NULL GROUP BY a.company.companyId")
     List<Object[]> sumFileSizePerCompany();
}
