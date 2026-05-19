package com.team.intranet.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team.intranet.dto.ChatConversationDto;
import com.team.intranet.dto.ChatMessageDto;
import com.team.intranet.dto.ChatPeerDto;
import com.team.intranet.entity.ChatAttachment;
import com.team.intranet.service.ChatService;
import com.team.intranet.service.ChatSseService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 채팅 API.
 *  - 모든 회원이 사용 가능 (isAuthenticated). 참여자 검증은 서비스에서.
 *  - 파일 첨부: multipart/form-data — text(선택) + files[] (선택). 둘 다 비면 거절.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatApiController {

    private final ChatService chatService;
    private final ChatSseService chatSseService;

    // ─── 실시간 수신 (SSE) ──────────────────────────────────────────

    /**
     * 본인 앞으로 들어오는 채팅 이벤트 스트림.
     *  - 클라이언트: new EventSource('/api/chat/stream') 로 연결.
     *  - 이벤트: "ready" (연결 핸드셰이크) / "message" ({conversationId, message: ChatMessageDto}).
     *  - 30분 후 timeout → 브라우저 EventSource 가 자동 재연결.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return chatSseService.subscribe(ms.getMemberId());
    }

    // ─── 회원 검색 (새 채팅) ─────────────────────────────────────────

    @GetMapping("/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatPeerDto>> listPeers(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(chatService.searchPeers(ms));
    }

    @GetMapping("/members/{memberId}/profile-image")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> profileImage(
            @PathVariable Long memberId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        byte[] img = chatService.getMemberProfileImage(ms, memberId);
        if (img == null || img.length == 0) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)  // mime 모르면 jpeg 폴백 (대부분 jpg/png — 브라우저 sniff)
            .body(img);
    }

    // ─── 대화방 ──────────────────────────────────────────────────────

    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatConversationDto>> listConversations(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(chatService.findMyConversations(ms));
    }

    /** 1:1 대화방 생성 또는 기존 반환. body: { peerId: number } */
    @PostMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatConversationDto> openConversation(
            @RequestBody Map<String, Long> body,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Long peerId = body == null ? null : body.get("peerId");
        return ResponseEntity.ok(chatService.getOrCreateConversation(ms, peerId));
    }

    // ─── 메시지 ──────────────────────────────────────────────────────

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatMessageDto>> listMessages(
            @PathVariable("id") Long conversationId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(chatService.findMessages(ms, conversationId));
    }

    /** multipart: text(선택, plain text part) + files(선택, 여러 개). 둘 다 비면 400. */
    @PostMapping(value = "/conversations/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatMessageDto> sendMessage(
            @PathVariable("id") Long conversationId,
            @RequestParam(value = "text", required = false) String text,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(chatService.sendMessage(ms, conversationId, text, files));
    }

    // ─── 첨부 다운로드 ────────────────────────────────────────────────

    @GetMapping("/attachments/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable("id") Long attachmentId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ChatAttachment att = chatService.loadAttachment(ms, attachmentId);
        MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
        try { if (att.getMimeType() != null) mt = MediaType.parseMediaType(att.getMimeType()); } catch (Exception ignored) { /* 폴백 유지 */ }
        // 이미지면 inline, 그 외엔 attachment (브라우저가 다운로드)
        boolean isImage = att.getMimeType() != null && att.getMimeType().toLowerCase().startsWith("image/");
        String disp = (isImage ? "inline" : "attachment") + "; filename=\"" + safeName(att.getFileName()) + "\"";
        return ResponseEntity.ok()
            .contentType(mt)
            .header(HttpHeaders.CONTENT_DISPOSITION, disp)
            .body(att.getData());
    }

    private String safeName(String n) {
        if (n == null) return "file";
        return n.replace("\"", "");
    }
}
