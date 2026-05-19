package com.team.intranet.dto.ai;

import java.time.format.DateTimeFormatter;

import com.team.intranet.entity.AiChatMessage;
import com.team.intranet.enums.AiChatRole;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 대화 메시지 1건. 프론트는 role 로 사용자/AI 말풍선 분기.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessageDto {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long messageId;
    private AiChatRole role;
    private String content;
    private String createdAt;

    public static AiChatMessageDto from(AiChatMessage m) {
        AiChatMessageDto dto = new AiChatMessageDto();
        dto.messageId = m.getMessageId();
        dto.role = m.getRole();
        dto.content = m.getContent();
        dto.createdAt = m.getCreatedAt() != null ? m.getCreatedAt().format(DT) : null;
        return dto;
    }
}
