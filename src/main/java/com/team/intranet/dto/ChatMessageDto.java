package com.team.intranet.dto;

import java.time.format.DateTimeFormatter;
import java.util.List;

import com.team.intranet.entity.ChatAttachment;
import com.team.intranet.entity.ChatMessage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long messageId;
    private Long senderId;
    private String senderName;
    private String text;
    private String createdAt;
    private List<ChatAttachmentDto> attachments;

    public static ChatMessageDto from(ChatMessage m, List<ChatAttachment> atts) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.messageId = m.getMessageId();
        dto.senderId = m.getSender() != null ? m.getSender().getMemberId() : null;
        dto.senderName = m.getSender() != null ? m.getSender().getName() : null;
        dto.text = m.getText();
        dto.createdAt = m.getCreatedAt() != null ? m.getCreatedAt().format(DT) : null;
        dto.attachments = (atts == null) ? List.of()
            : atts.stream().map(ChatAttachmentDto::from).toList();
        return dto;
    }
}
