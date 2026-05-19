package com.team.intranet.dto;

import com.team.intranet.entity.ChatAttachment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatAttachmentDto {

    private Long attachmentId;
    private String fileName;
    private String mimeType;
    private Long byteSize;
    /** 다운로드/표시 URL */
    private String url;
    /** 이미지 여부 — 프론트가 인라인 표시 여부 판단. */
    private boolean isImage;

    public static ChatAttachmentDto from(ChatAttachment a) {
        ChatAttachmentDto dto = new ChatAttachmentDto();
        dto.attachmentId = a.getAttachmentId();
        dto.fileName = a.getFileName();
        dto.mimeType = a.getMimeType();
        dto.byteSize = a.getByteSize();
        dto.url = "/api/chat/attachments/" + a.getAttachmentId();
        dto.isImage = a.getMimeType() != null && a.getMimeType().toLowerCase().startsWith("image/");
        return dto;
    }
}
