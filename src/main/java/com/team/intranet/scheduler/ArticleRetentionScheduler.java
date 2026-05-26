package com.team.intranet.scheduler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.team.intranet.repository.ArticleRepository;
import com.team.intranet.repository.AttachmentRepository;
import java.time.LocalDateTime;
import java.util.List;

import com.team.intranet.enums.board.BoardType;
import com.team.intranet.entity.Article;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleRetentionScheduler {

    private final ArticleRepository articleRepository;
    private final AttachmentRepository attachmentRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredDeletedArticles() {
        LocalDateTime now = LocalDateTime.now();
        int totalPurged = 0;

        for (BoardType type : BoardType.values()) {
            LocalDateTime threshold = now.minusDays(type.getRetentionDays());
            List<Article> targets = articleRepository
                .findByIsDeletedTrueAndBoard_BoardTypeAndDeletedAtBefore(type, threshold);
            if (targets.isEmpty()) continue;

            // BoardType 당 SQL 2회 (attachment bulk + article bulk) — 기존 N+1 제거.
            List<Long> ids = targets.stream().map(Article::getArticleId).toList();
            attachmentRepository.deleteByArticleIdIn(ids);
            articleRepository.deleteAllInBatch(targets);
            totalPurged += targets.size();
        }

        if (totalPurged > 0) {
            log.info("[ArticleRetentionScheduler] purged {} expired articles", totalPurged);
        }
    }
}
