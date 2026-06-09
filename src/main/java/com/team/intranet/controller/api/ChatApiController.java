package com.team.intranet.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team.intranet.dto.ChatConversationDto;
import com.team.intranet.dto.ChatMessageDto;
import com.team.intranet.dto.ChatPeerDto;
import com.team.intranet.entity.ChatAttachment;
import com.team.intranet.service.ChatService;
import com.team.intranet.service.ChatSseService;
import com.team.intranet.config.AuthenticatedMember;
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
     *  - 이벤트: "ready" (연결 핸드셰이크) / "chat-message" ({conversationId, message: ChatMessageDto}).
     *  - 30분 후 timeout → 브라우저 EventSource 가 자동 재연결.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream(@AuthenticatedMember MemberSession ms) {
        return chatSseService.subscribe(ms.getMemberId());
    }

    // ─── 회원 검색 (새 채팅) ─────────────────────────────────────────

    @GetMapping("/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatPeerDto>> listPeers(
            @AuthenticatedMember MemberSession ms) {
        return ResponseEntity.ok(chatService.searchPeers(ms));
    }

    @GetMapping("/members/{memberId}/profile-image")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> profileImage(
            @PathVariable Long memberId,
            @AuthenticatedMember MemberSession ms) {
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
            @AuthenticatedMember MemberSession ms) {
        return ResponseEntity.ok(chatService.findMyConversations(ms));
    }

    /** 본인의 채팅 미확인 메시지 총합 — FAB 우상단 배지 + 헤더 배지 공통. */
    @GetMapping("/unread")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> totalUnread(
            @AuthenticatedMember MemberSession ms) {
        return ResponseEntity.ok(chatService.countMyChatUnreadTotal(ms));
    }

    /** 채팅방 진입 = 그 대화방의 본인 알림 일괄 삭제 (읽음 처리). */
    @PostMapping("/conversations/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markRead(
            @PathVariable("id") Long conversationId,
            @AuthenticatedMember MemberSession ms) {
        chatService.markConversationRead(ms, conversationId);
        return ResponseEntity.ok().build();
    }

    /** 1:1 대화방 생성 또는 기존 반환. body: { peerId: number } */
    @PostMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatConversationDto> openConversation(
            @RequestBody Map<String, Long> body,
            @AuthenticatedMember MemberSession ms) {
        Long peerId = body == null ? null : body.get("peerId");
        return ResponseEntity.ok(chatService.getOrCreateConversation(ms, peerId));
    }

    /**
     * 대화방 제목 수정 (per-user) — 점 3개 메뉴 [제목 수정]. body: { "title": "새 제목" }.
     *  - title 이 null/blank 면 기본 제목(상대 이름)으로 복귀.
     */
    @PostMapping("/conversations/{id}/title")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatConversationDto> renameConversation(
            @PathVariable("id") Long conversationId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticatedMember MemberSession ms) {
        String title = body == null ? null : body.get("title");
        return ResponseEntity.ok(chatService.renameConversation(ms, conversationId, title));
    }

    /** 대화방 고정/고정 해제 토글 (per-user). 응답: { pinned: boolean }. */
    @PostMapping("/conversations/{id}/pin")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Boolean>> togglePinConversation(
            @PathVariable("id") Long conversationId,
            @AuthenticatedMember MemberSession ms) {
        boolean pinned = chatService.togglePinConversation(ms, conversationId);
        return ResponseEntity.ok(Map.of("pinned", pinned));
    }

    /**
     * 대화방 나가기 (per-user) — 점 3개 메뉴 [채팅방 나가기].
     *  - 본인 시점에서만 숨김. 양쪽 모두 나가면 메시지/첨부까지 완전 삭제.
     *  - 새 메시지 도착 시 자동 복귀.
     */
    @DeleteMapping("/conversations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> leaveConversation(
            @PathVariable("id") Long conversationId,
            @AuthenticatedMember MemberSession ms) {
        chatService.leaveConversation(ms, conversationId);
        return ResponseEntity.noContent().build();
    }

    // ─── 메시지 ──────────────────────────────────────────────────────

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatMessageDto>> listMessages(
            @PathVariable("id") Long conversationId,
            @AuthenticatedMember MemberSession ms) {
        return ResponseEntity.ok(chatService.findMessages(ms, conversationId));
    }

    /** multipart: text(선택, plain text part) + files(선택, 여러 개). 둘 다 비면 400. */
    @PostMapping(value = "/conversations/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatMessageDto> sendMessage(
            @PathVariable("id") Long conversationId,
            @RequestParam(value = "text", required = false) String text,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @AuthenticatedMember MemberSession ms) {
        return ResponseEntity.ok(chatService.sendMessage(ms, conversationId, text, files));
    }

    // ─── 첨부 다운로드 ────────────────────────────────────────────────

    @GetMapping("/attachments/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable("id") Long attachmentId,
            @AuthenticatedMember MemberSession ms) {
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
