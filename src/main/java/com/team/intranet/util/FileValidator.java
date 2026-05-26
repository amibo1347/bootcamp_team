package com.team.intranet.util;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.team.intranet.enums.ErrorCode;
import com.team.intranet.exception.BusinessException;

/**
 * 업로드 파일 공통 검증 — Attachment/ApprovalAttachment/Upload 세 컨트롤러에서 반복되던 검증 로직 추출.
 *  - 위반 시 BusinessException 으로 통일 (기존엔 컨트롤러마다 ResponseEntity 직접 반환).
 *  - GlobalExceptionHandler 가 ErrorCode 의 status/message 로 변환.
 */
@Component
public class FileValidator {

    private static final long ATTACHMENT_MAX_BYTES = 50L * 1024 * 1024;  // 일반 첨부
    private static final long IMAGE_MAX_BYTES      = 10L * 1024 * 1024;  // 인라인 이미지
    private static final Set<String> IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    /** 실행 가능 파일 차단. 게시글·결재 첨부 양쪽에서 같은 목록을 쓰던 것을 통합. */
    private static final Set<String> BLOCKED_EXT =
            Set.of("exe", "bat", "sh", "cmd", "com", "msi", "scr", "jar");

    /** 일반 첨부파일 — 50MB 제한, 실행 가능 확장자 차단. (게시글·결재 첨부) */
    public void validateAttachment(MultipartFile file) {
        requireNonEmpty(file);
        if (file.getSize() > ATTACHMENT_MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        String ext = (dot >= 0) ? name.substring(dot + 1).toLowerCase() : "";
        if (BLOCKED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    /** 인라인 이미지 — 10MB + image/(png|jpeg|gif|webp) 만. (게시글 본문 이미지) */
    public void validateImage(MultipartFile file) {
        requireNonEmpty(file);
        if (!IMAGE_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        if (file.getSize() > IMAGE_MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    private void requireNonEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
