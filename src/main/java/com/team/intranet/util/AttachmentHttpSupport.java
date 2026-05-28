package com.team.intranet.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * 첨부파일 컨트롤러 공용 응답 헬퍼.
 *
 *  AttachmentApiController(게시글 첨부) 와 ApprovalAttachmentApiController(전자결재 첨부) 가
 *  완전히 동일한 형태로 사용하던 두 패턴을 한 곳에 모은다:
 *   - 업로드 성공 응답 body: { id, filename, size }
 *   - 다운로드 응답 헤더: Content-Type + Content-Disposition (RFC 5987 UTF-8 파일명 인코딩)
 *
 *  ※ ChatApiController 는 inline / "filename=" 방식으로 다르게 처리하므로 여기 통합 대상 아님.
 */
public final class AttachmentHttpSupport {

    private AttachmentHttpSupport() {
        // util 클래스 인스턴스화 방지
    }

    /**
     * 업로드 성공 응답 body — 컨트롤러 두 곳에서 동일하게 사용하던 Map 구조.
     */
    public static Map<String, Object> uploadResponseBody(Long id, String filename, Long size) {
        return Map.of(
                "id", id,
                "filename", filename,
                "size", size
        );
    }

    /**
     * 첨부 다운로드 응답.
     *  - 한글 파일명은 RFC 5987 (UTF-8 URL-encoded) 형식으로 Content-Disposition 에 박는다.
     *  - contentType 이 null 이면 application/octet-stream 으로 폴백.
     */
    public static ResponseEntity<byte[]> downloadResponse(byte[] data, String filename, String contentType) {
        String safeName = filename == null || filename.isBlank() ? "file" : filename;
        String encodedName = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE,
                        contentType == null ? "application/octet-stream" : contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .body(data);
    }
}
