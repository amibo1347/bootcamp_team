package com.team.intranet.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

import com.team.intranet.enums.Preface;
import com.team.intranet.entity.Alert;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertDto {
    private Long alertId;
    private Preface preface;
    private String title;
    private String content;
    private String link;
    private boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    private Long senderId;
    private String senderName;
    private Long articleId;
    private Long calendarId;
    private Long commentId;

    public static AlertDto from(Alert alert){
        AlertDto dto = new AlertDto();
        dto.setAlertId(alert.getAlertId());
        dto.setPreface(alert.getPreface());
        dto.setTitle(alert.getTitle());
        dto.setContent(alert.getContent());
        dto.setLink(alert.getLink());
        dto.setRead(alert.isRead());
        dto.setCreatedAt(alert.getCreatedAt());
        dto.setReadAt(alert.getReadAt());
        if (alert.getSender() != null) {
            dto.setSenderId(alert.getSender().getMemberId());
            dto.setSenderName(alert.getSender().getName());
        }
        if (alert.getArticle() != null) {
            dto.setArticleId(alert.getArticle().getArticleId());
        }
        if (alert.getCalendar() != null) {
            dto.setCalendarId(alert.getCalendar().getCalendarId());
        }
        if (alert.getComment() != null) {
            dto.setCommentId(alert.getComment().getCommentId());
        }
        return dto;
    }
}
