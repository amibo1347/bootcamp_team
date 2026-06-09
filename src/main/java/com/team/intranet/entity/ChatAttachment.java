package com.team.intranet.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.Setter;

/**
 * 채팅 메시지 첨부파일. 데이터는 BLOB 로 저장 (MVP — 외부 스토리지 분리는 v2).
 * ※ data 는 LAZY 로 두어 목록 조회 시 자동 로딩되지 않게 함.
 */
@Entity
@Table(name = "tbl_chat_attachment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long attachmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "mime_type", length = 200)
    private String mimeType;

    @Column(name = "byte_size")
    private Long byteSize;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", nullable = false)
    private byte[] data;

    public static ChatAttachment of(ChatMessage message, String fileName, String mimeType, long size, byte[] data) {
        return ChatAttachment.builder()
            .message(message)
            .fileName(fileName)
            .mimeType(mimeType)
            .byteSize(size)
            .data(data)
            .build();
    }
}
