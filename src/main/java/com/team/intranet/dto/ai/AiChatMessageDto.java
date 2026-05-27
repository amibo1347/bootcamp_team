package com.team.intranet.dto.ai;

import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.intranet.entity.AiChatMessage;
import com.team.intranet.enums.AiChatRole;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 대화 메시지 1건. 프론트는 role 로 사용자/AI 말풍선 분기.
 *  - proposal: ASSISTANT 메시지가 액션 제안 (일정/결재) 을 포함하면 채워짐.
 *               null 이면 일반 텍스트 응답.
 *  - proposalApplied: 사용자가 이미 [등록] 한 제안이면 true → 프론트가 버튼 비활성화.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessageDto {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long messageId;
    private AiChatRole role;
    private String content;
    private String createdAt;
    /** 액션 제안 메타데이터 (raw JSON). type 으로 분기 ("calendar" / "leave" 등). */
    private JsonNode proposal;
    private Boolean proposalApplied;
    /**
     * 세션 제목이 이 응답으로 갱신됐다면 새 제목 — 프론트가 사이드바/헤더를 즉시 갱신.
     * 갱신이 없는 일반 응답에선 null.
     */
    private String sessionTitle;

    public static AiChatMessageDto from(AiChatMessage m) {
        AiChatMessageDto dto = new AiChatMessageDto();
        dto.messageId = m.getMessageId();
        dto.role = m.getRole();
        dto.content = m.getContent();
        dto.createdAt = m.getCreatedAt() != null ? m.getCreatedAt().format(DT) : null;
        dto.proposalApplied = m.getProposalApplied();
        if (m.getProposalJson() != null && !m.getProposalJson().isBlank()) {
            try {
                dto.proposal = MAPPER.readTree(m.getProposalJson());
            } catch (Exception ignored) {
                // 깨진 JSON 이면 그냥 null 로 — 사용자에겐 일반 메시지로 보임
            }
        }
        return dto;
    }
}
