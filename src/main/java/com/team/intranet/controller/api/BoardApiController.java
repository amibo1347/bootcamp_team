package com.team.intranet.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.team.intranet.dto.BoardDto;
import com.team.intranet.service.BoardService;
import com.team.intranet.config.AuthenticatedMember;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 게시판 관리 API.
 *  - 프론트(board.js) 는 응답 body 를 사용하지 않고 location.reload() 만 호출 → 204 No Content 로 응답.
 *  - 과거에는 "redirect:/admin/board/list" 를 반환했으나 fetch 가 follow 해서 불필요한 view 렌더링이 발생하던 패턴이었음.
 */
@RestController
@RequestMapping("/api/admin/board")
@RequiredArgsConstructor
public class BoardApiController {

    private final BoardService boardService;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
    public ResponseEntity<Void> createBoard(
            @AuthenticatedMember MemberSession ms,
            @RequestBody BoardDto dto) {
        boardService.createBoard(ms, dto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/update/{boardId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
    public ResponseEntity<Void> updateBoard(
            @AuthenticatedMember MemberSession ms,
            @PathVariable Long boardId,
            @RequestBody BoardDto boardDto) {
        boardService.updateBoard(ms, boardId, boardDto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/delete/{boardId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
    public ResponseEntity<Void> deleteBoard(
            @AuthenticatedMember MemberSession ms,
            @PathVariable Long boardId) {
        boardService.deleteBoard(ms, boardId);
        return ResponseEntity.noContent().build();
    }
}
