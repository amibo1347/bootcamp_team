package com.team.intranet.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.dto.AlertDto;
import com.team.intranet.entity.Alert;
import com.team.intranet.entity.Article;
import com.team.intranet.entity.Calendar;
import com.team.intranet.entity.Comment;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.Preface;
import com.team.intranet.enums.Visibility;
import com.team.intranet.enums.member.Status;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.AlertRepository;
import com.team.intranet.repository.CalendarRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AlertService {

    // 일정 시작 24시간 전 1회 알림. 스캐너가 매분 돌므로 ±1분 오차 발생 가능.
    private static final int CALENDAR_LEAD_MINUTES = 1440;

    private final AlertRepository alertRepository;
    private final MemberRepository memberRepository;
    private final CalendarRepository calendarRepository;

    // ============================================================
    // 일정 알림 (스케줄러)
    // ============================================================

    /** 매분 실행 — 시작 24시간 안에 들어온 일정에 대해 (calendar, recipient) 단위로 1회 알림. */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void scanCalendarAlerts() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime horizon = now.plusMinutes(CALENDAR_LEAD_MINUTES + 5);

        List<Calendar> due = calendarRepository.findUpcomingForAlert(now, horizon);
        if (due.isEmpty()) return;

        // 대상 캘린더 전체의 발송 기록을 한 번의 쿼리로 로드 → calendarId → recipientId 집합
        Map<Long, Set<Long>> alreadySent = alertRepository.findByCalendarIn(due).stream()
            .filter(a -> a.getCalendar() != null && a.getRecipient() != null)
            .collect(Collectors.groupingBy(
                a -> a.getCalendar().getCalendarId(),
                Collectors.mapping(a -> a.getRecipient().getMemberId(), Collectors.toSet())
            ));

        for (Calendar c : due) {
            if (c.getStartAt() == null) continue;
            if (now.isBefore(c.getStartAt().minusMinutes(CALENDAR_LEAD_MINUTES))) continue;

            Set<Long> sent = alreadySent.getOrDefault(c.getCalendarId(), Set.of());
            for (Member r : resolveCalendarRecipients(c).values()) {
                if (sent.contains(r.getMemberId())) continue;
                sendCalendarAlert(c, r);
            }
        }
    }

    public void sendCalendarAlert(Calendar calendar, Member recipient) {
        Preface preface = Preface.CALENDAR_ALERT;
        Alert alert = baseBuilder(preface, recipient)
            .title(preface.getLabel() + " " + calendar.getTitle())
            .content("1일 뒤 일정이 시작됩니다")
            .link("/calendars/" + calendar.getCalendarId())
            .calendar(calendar)
            .sender(calendar.getMember())
            .expiresAt(calendar.getStartAt().plusMinutes(30))
            .build();
        alertRepository.save(alert);
    }

    private Map<Long, Member> resolveCalendarRecipients(Calendar calendar) {
        Map<Long, Member> recipients = new HashMap<>();
        Status active = Status.JOIN;

        // 작성자 본인 — 활성 상태일 때만 포함
        Member owner = calendar.getMember();
        if (owner != null && owner.getStatus() == active) {
            recipients.put(owner.getMemberId(), owner);
        }

        Visibility v = calendar.getVisibility();
        if (v == null || v == Visibility.PRIVATE) return recipients;

        List<Member> shared = switch (v) {
            case COMPANY -> (owner != null && owner.getCompany() != null)
                ? memberRepository.findByStatusAndCompanyCompanyId(active, owner.getCompany().getCompanyId())
                : List.of();
            case DEPARTMENT -> memberRepository.findJoinByCalendarDeptShares(calendar, active);
            case SPECIFIC   -> memberRepository.findJoinByCalendarMemberShares(calendar, active);
            default         -> List.of();
        };
        shared.forEach(m -> recipients.put(m.getMemberId(), m));
        return recipients;
    }

    // ============================================================
    // 게시글 알림 — 호출자(예: ArticleService.createArticle) 트랜잭션과 분리
    // ============================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendArticleNewAlert(Article article, Member recipient) {
        Preface preface = Preface.ARTICLE_NEW;
        Member sender = article.isAnonymous() ? null : article.getAuthor();   // 익명 게시판이면 sender 마스킹

        Alert alert = baseBuilder(preface, recipient)
            .title(preface.getLabel() + " " + article.getTitle())
            .content("새 글이 올라왔습니다")
            .link(articleLink(article))
            .article(article)
            .sender(sender)
            .expiresAt(LocalDateTime.now().plusDays(7))
            .build();
        alertRepository.save(alert);
    }

    // ============================================================
    // 댓글/답글 알림
    // ============================================================

    @Transactional
    public void sendCommentAlert(Comment comment, Member recipient, Article article) {
        if (recipient == null) return;
        if (isSameMember(comment.getAuthor(), recipient)) return;
        // 답글이면 sendCommentReplyAlert가 부모 작성자에게 보내므로 동일인 중복 방지
        if (comment.getParent() != null
                && isSameMember(comment.getParent().getAuthor(), recipient)) return;

        Preface preface = Preface.ARTICLE_COMMENT;
        Alert alert = baseBuilder(preface, recipient)
            .title(preface.getLabel() + " " + article.getTitle())
            .content(summarize(comment.getContent()))
            .link(articleLink(article))
            .article(article)
            .comment(comment)
            .sender(comment.getAuthor())
            .expiresAt(LocalDateTime.now().plusDays(7))
            .build();
        alertRepository.save(alert);
    }

    @Transactional
    public void sendCommentReplyAlert(Comment reply, Article article) {
        if (reply.getParent() == null) return;

        Member recipient = reply.getParent().getAuthor();
        if (recipient == null) return;
        if (isSameMember(reply.getAuthor(), recipient)) return;

        Preface preface = Preface.COMMENT_REPLY;
        Alert alert = baseBuilder(preface, recipient)
            .title(preface.getLabel() + " " + article.getTitle())
            .content(summarize(reply.getContent()))
            .link(articleLink(article))
            .article(article)
            .comment(reply)
            .sender(reply.getAuthor())
            .expiresAt(LocalDateTime.now().plusDays(7))
            .build();
        alertRepository.save(alert);
    }

    // ============================================================
    // 알림함 (사용자용 — 조회 / 읽음 / 삭제)
    // ============================================================

    @Transactional(readOnly = true)
    public List<AlertDto> getMyAlerts(MemberSession ms) {
        Member me = loadMember(ms);
        return alertRepository.findByRecipientOrderByCreatedAtDesc(me).stream()
            .map(AlertDto::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertDto> getMyUnreadAlerts(MemberSession ms) {
        Member me = loadMember(ms);
        return alertRepository.findByRecipientAndIsReadFalseOrderByCreatedAtDesc(me).stream()
            .map(AlertDto::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public long getMyUnreadCount(MemberSession ms) {
        Member me = loadMember(ms);
        return alertRepository.countByRecipientAndIsReadFalse(me);
    }

    @Transactional
    public void markAsRead(MemberSession ms, Long alertId) {
        Member me = loadMember(ms);
        Alert alert = alertRepository.findByAlertIdAndRecipient(alertId, me)
            .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_NOT_FOUND));
        if (!alert.isRead()) {
            alert.setRead(true);
            alert.setReadAt(LocalDateTime.now());
        }
    }

    @Transactional
    public void markAllAsRead(MemberSession ms) {
        Member me = loadMember(ms);
        LocalDateTime now = LocalDateTime.now();
        alertRepository.findByRecipientAndIsReadFalseOrderByCreatedAtDesc(me).forEach(a -> {
            a.setRead(true);
            a.setReadAt(now);
        });
    }

    @Transactional
    public void deleteAlert(MemberSession ms, Long alertId) {
        Member me = loadMember(ms);
        Alert alert = alertRepository.findByAlertIdAndRecipient(alertId, me)
            .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_NOT_FOUND));
        alertRepository.delete(alert);
    }

    @Transactional
    public void deleteAllMyAlerts(MemberSession ms) {
        Member me = loadMember(ms);
        alertRepository.deleteAllByRecipient(me);
    }

    private Member loadMember(MemberSession ms) {
        return memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    // ============================================================
    // 공통 헬퍼
    // ============================================================

    private Alert.AlertBuilder baseBuilder(Preface preface, Member recipient) {
        return Alert.builder()
            .preface(preface)
            .recipient(recipient)
            .isRead(false);
    }

    private boolean isSameMember(Member a, Member b) {
        return a != null && b != null && a.getMemberId().equals(b.getMemberId());
    }

    private String articleLink(Article article) {
        return "/board/" + article.getBoard().getBoardId() + "/articles/" + article.getArticleId();
    }

    private String summarize(String text) {
        if (text == null) return null;
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 50 ? oneLine.substring(0, 50) + "…" : oneLine;
    }
}
