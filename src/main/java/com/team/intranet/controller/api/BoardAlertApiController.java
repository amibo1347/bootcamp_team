package com.team.intranet.controller.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.service.BoardService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardAlertApiController {

    private final BoardService boardService;

    // 게시판 알림 버튼 상태 조회 — 페이지 진입 시 종 아이콘 초기 렌더링용
    @GetMapping("/{boardId}/alert")
    public ResponseEntity<Map<String, Boolean>> getAlertState(
            @PathVariable Long boardId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean enabled = boardService.isAlertOn(ms, boardId);
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    // 게시판 알림 버튼 토글 — 클릭 시 호출, 응답으로 새 상태 반환
    @PostMapping("/{boardId}/alert/toggle")
    public ResponseEntity<Map<String, Boolean>> toggleAlert(
            @PathVariable Long boardId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean nextState = boardService.toggleAlert(ms, boardId);
        return ResponseEntity.ok(Map.of("enabled", nextState));
    }
}
