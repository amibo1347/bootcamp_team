package com.team.intranet.dto.ai;

import java.time.format.DateTimeFormatter;

import com.team.intranet.entity.AiChatMessage;
import com.team.intranet.entity.AiChatSession;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 비서 세션 목록 항목 — 프론트 renderAiList() 와 매칭.
 *  - sessionId, title, lastMessagePreview, updatedAt
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatSessionDto {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long sessionId;
    private String title;
    private String lastMessagePreview;
    private String updatedAt;
    /** 본인이 고정했는지 — 프론트가 ⭐ 표시 + 정렬 보조용 (정렬은 서버가 1차 수행). */
    private boolean pinned;

    public static AiChatSessionDto from(AiChatSession s, AiChatMessage lastMsg) {
        AiChatSessionDto dto = new AiChatSessionDto();
        dto.sessionId = s.getSessionId();
        dto.title = s.getTitle();
        if (lastMsg != null && lastMsg.getContent() != null) {
            String preview = lastMsg.getContent().replaceAll("\\s+", " ").trim();
            if (preview.length() > 60) preview = preview.substring(0, 60) + "…";
            dto.lastMessagePreview = preview;
        }
        dto.updatedAt = s.getUpdatedAt() != null ? s.getUpdatedAt().format(DT) : null;
        dto.pinned = s.isPinned();
        return dto;
    }
}
