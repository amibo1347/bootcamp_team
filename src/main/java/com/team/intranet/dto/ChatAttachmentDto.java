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
    /**
     * 첨부 종류: "image" | "video" | "file".
     *  - 프론트가 인라인 렌더(img/video) 분기 시 사용.
     *  - boolean 필드(isXxx) 는 Jackson + Lombok 조합에서 직렬화 키 불일치 이슈가 있어 String 으로 통일.
     */
    private String kind;

    public static ChatAttachmentDto from(ChatAttachment a) {
        ChatAttachmentDto dto = new ChatAttachmentDto();
        dto.attachmentId = a.getAttachmentId();
        dto.fileName = a.getFileName();
        dto.mimeType = a.getMimeType();
        dto.byteSize = a.getByteSize();
        dto.url = "/api/chat/attachments/" + a.getAttachmentId();
        dto.kind = resolveKind(a.getMimeType());
        return dto;
    }

    private static String resolveKind(String mime) {
        if (mime == null) return "file";
        String m = mime.toLowerCase();
        if (m.startsWith("image/")) return "image";
        if (m.startsWith("video/")) return "video";
        return "file";
    }
}
