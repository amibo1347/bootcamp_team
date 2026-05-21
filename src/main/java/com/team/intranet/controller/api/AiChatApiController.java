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

    /**
     * 일정 제안 [등록] 확정 — body: { "messageId": 123 }.
     * 응답: 새로 추가된 확정 알림 메시지 (assistant role).
     */
    @PostMapping("/calendar/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiChatMessageDto> confirmCalendar(
            @RequestBody Map<String, Long> body,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Long messageId = body == null ? null : body.get("messageId");
        if (messageId == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(aiChatService.confirmCalendarProposal(ms, messageId));
    }

    /**
     * 휴가 신청 제안 [신청] 확정 —
     * body: { "messageId": 123, "vacationType": "ANNUAL_PAID_LEAVE", "attachmentIds": [1, 2] }.
     * vacationType 은 카드 dropdown 에서 사용자가 확정한 휴가 종류 (생략 시 AI 추론값 사용).
     * attachmentIds 는 카드 하단에서 미리 업로드한 첨부파일 id (선택 사항).
     * VACATION 양식으로 전자결재 기안. 응답: 확정 알림 메시지 (assistant role).
     */
    @PostMapping("/leave/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiChatMessageDto> confirmLeave(
            @RequestBody Map<String, Object> body,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Object midObj = body == null ? null : body.get("messageId");
        if (midObj == null) return ResponseEntity.badRequest().build();
        Long messageId;
        try {
            messageId = Long.valueOf(midObj.toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
        Object vt = body.get("vacationType");
        String vacationType = vt == null ? null : vt.toString();

        // 첨부파일 id — JSON 배열. 숫자가 아닌 항목은 무시.
        List<Long> attachmentIds = new java.util.ArrayList<>();
        Object aidObj = body.get("attachmentIds");
        if (aidObj instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) continue;
                try {
                    attachmentIds.add(Long.valueOf(o.toString()));
                } catch (NumberFormatException ignored) {
                    // 잘못된 항목은 건너뜀
                }
            }
        }
        return ResponseEntity.ok(
            aiChatService.confirmLeaveProposal(ms, messageId, vacationType, attachmentIds));
    }
}
