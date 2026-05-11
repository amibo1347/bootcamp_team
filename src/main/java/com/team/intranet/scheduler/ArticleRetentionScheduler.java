package com.team.intranet.scheduler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import lombok.RequiredArgsConstructor;
import com.team.intranet.repository.ArticleRepository;
import com.team.intranet.repository.AttachmentRepository;
import java.time.LocalDateTime;
import java.util.List;

import com.team.intranet.enums.board.BoardType;
import com.team.intranet.entity.Article;
import com.team.intranet.entity.ArticleAttachment;

@Component
@RequiredArgsConstructor
public class ArticleRetentionScheduler {
    
    private final ArticleRepository articleRepository;
    private final AttachmentRepository attachmentRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredDeletedArticles() {
        LocalDateTime now = LocalDateTime.now();

        for(BoardType type : BoardType.values()){
            LocalDateTime threshoId = now.minusDays(type.getRetentionDays());
            List<Article> targets = articleRepository
                .findByIsDeletedTrueAndBoard_BoardTypeAndDeletedAtBefore(type, threshoId);
            for (Article a : targets){
                List<ArticleAttachment> atts = 
                    attachmentRepository.findByArticle_ArticleId(a.getArticleId());

                if(!atts.isEmpty()) attachmentRepository.deleteAll(atts);
                articleRepository.delete(a);
            }
        }
    }
}
