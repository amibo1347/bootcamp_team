package com.team.intranet.controller.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;

import com.team.intranet.service.BoardService;
import com.team.intranet.dto.BoardDto;
import com.team.intranet.session.MemberSession;
import com.team.intranet.enums.member.Role;
import com.team.intranet.entity.Board;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.SessionAttribute;
import java.util.List;

@Controller
@RequestMapping("/api/admin/board")
@RequiredArgsConstructor
public class BoardApiController {

    private final BoardService boardService;

    // 게시판 생성 처리
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')") 
    public String createBoard(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @RequestBody BoardDto dto) {


        // 1. 게시판 생성 로직 수행
        boardService.createBoard(ms, dto);

        // 2. 게시판 생성 후 게시판 관리 페이지로 리다이렉트
        return "redirect:/admin/board/list";
    }

        // 게시판 수정 처리
    @PostMapping("/update/{boardId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
    public String updateBoard(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @PathVariable Long boardId,
            @RequestBody BoardDto boardDto) {

        // 1. 게시판 수정 로직 수행
        boardService.updateBoard(ms, boardId, boardDto);

        // 2. 게시판 수정 후 게시판 관리 페이지로 리다이렉트
        return "redirect:/admin/board/list";
    }

    // 게시판 삭제 처리
    @PostMapping("/delete/{boardId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
    public String deleteBoard(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @PathVariable Long boardId) {

        // 1. 게시판 삭제 로직 수행
        boardService.deleteBoard(ms, boardId);

        // 2. 게시판 삭제 후 게시판 관리 페이지로 리다이렉트
        return "redirect:/admin/board/list";
    }

}
