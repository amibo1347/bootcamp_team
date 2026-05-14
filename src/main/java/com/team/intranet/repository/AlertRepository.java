package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.team.intranet.entity.Alert;
import com.team.intranet.entity.Calendar;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.Preface;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByRecipientOrderByCreatedAtDesc(Member recipient);
    List<Alert> findByRecipientAndIsReadFalseOrderByCreatedAtDesc(Member recipient);
    long countByRecipientAndIsReadFalse(Member recipient);
    Optional<Alert> findByAlertIdAndRecipient(Long alertId, Member recipient);
    void deleteAllByRecipient(Member recipient);

    @Modifying
    @Query("DELETE FROM Alert a WHERE a.expiresAt IS NOT NULL AND a.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    // 일정 알림 스캐너용 — 대상 캘린더 전체의 발송 기록을 한 번에 로드 (중복 발송 방지)
    List<Alert> findByCalendarIn(Collection<Calendar> calendars);

    // 같은 게시글의 댓글/답글 알림 일괄 삭제 (읽음 = 삭제 정책)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Alert a " +
           "WHERE a.recipient = :recipient AND a.article.articleId = :articleId " +
           "AND a.preface IN (:prefaces)")
    int deleteByRecipientAndArticleAndPrefaces(
        @Param("recipient") Member recipient,
        @Param("articleId") Long articleId,
        @Param("prefaces") Collection<Preface> prefaces
    );
}
