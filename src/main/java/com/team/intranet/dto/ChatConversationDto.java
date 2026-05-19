package com.team.intranet.dto;

import java.time.format.DateTimeFormatter;

import com.team.intranet.entity.ChatConversation;
import com.team.intranet.entity.ChatMessage;
import com.team.intranet.entity.Member;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 채팅 목록 항목 — 상대 정보 + 최근 메시지 미리보기. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversationDto {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long conversationId;
    private ChatPeerDto peer;
    /** 마지막 메시지 미리보기. 텍스트 없고 첨부만이면 "[파일]". */
    private String lastMessagePreview;
    /** 마지막 발신자가 본인이면 true → "나: ..." 접두 노출용. */
    private boolean lastSenderIsMe;
    /** ISO datetime. */
    private String updatedAt;

    public static ChatConversationDto from(ChatConversation conv, Long meId, ChatMessage last) {
        ChatConversationDto dto = new ChatConversationDto();
        dto.conversationId = conv.getConversationId();
        Member other = conv.otherSide(meId);
        dto.peer = other != null ? ChatPeerDto.fromEntity(other) : null;
        if (last != null) {
            String preview = (last.getText() != null && !last.getText().isBlank())
                ? last.getText()
                : "[파일]";
            if (preview.length() > 60) preview = preview.substring(0, 60) + "…";
            dto.lastMessagePreview = preview;
            dto.lastSenderIsMe = last.getSender() != null && last.getSender().getMemberId().equals(meId);
        }
        dto.updatedAt = conv.getUpdatedAt() != null ? conv.getUpdatedAt().format(DT) : null;
        return dto;
    }
}
