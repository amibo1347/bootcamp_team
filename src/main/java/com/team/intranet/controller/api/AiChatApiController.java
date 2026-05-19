package com.team.intranet.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.dto.ai.AiChatMessageDto;
import com.team.intranet.dto.ai.AiChatSessionDto;
import com.team.intranet.service.ai.AiChatService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * AI 비서 REST API — 메신저 패널의 AI 탭이 호출.
 *  GET    /api/ai/conversations                    : 내 세션 목록
 *  POST   /api/ai/conversations                    : 새 세션
 *  DELETE /api/ai/conversations/{id}               : 세션 삭제
 *  GET    /api/ai/conversations/{id}/messages      : 메시지 히스토리
 *  POST   /api/ai/conversations/{id}/messages      : 새 USER 메시지 → ASSISTANT 응답 즉시 반환
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatApiController {

    private final AiChatService aiChatService;

    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AiChatSessionDto>> listSessions(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(aiChatService.findMySessions(ms));
    }

    @PostMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiChatSessionDto> createSession(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(aiChatService.createSession(ms));
    }

    @DeleteMapping("/conversations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteSession(
            @PathVariable("id") Long sessionId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        aiChatService.deleteSession(ms, sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AiChatMessageDto>> listMessages(
            @PathVariable("id") Long sessionId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(aiChatService.findMessages(ms, sessionId));
    }

    /** body: { "content": "사용자 메시지" }  →  response: ASSISTANT 메시지 1건. */
    @PostMapping("/conversations/{id}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiChatMessageDto> sendMessage(
            @PathVariable("id") Long sessionId,
            @RequestBody Map<String, String> body,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String content = body == null ? null : body.get("content");
        return ResponseEntity.ok(aiChatService.sendMessage(ms, sessionId, content));
    }
}
