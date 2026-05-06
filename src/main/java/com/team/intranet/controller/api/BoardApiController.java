package com.team.intranet.controller.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import lombok.RequiredArgsConstructor;

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
    public String createBoard(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @RequestBody BoardDto dto) {

        if (ms == null || ms.getCompanyId() == null) {
            return "redirect:/member/login";
        }

        if (ms.getRole() == Role.USER) {
            return "redirect:/member/login";
        }

        // 1. 게시판 생성 로직 수행
        boardService.createBoard(ms, dto);

        // 2. 게시판 생성 후 게시판 관리 페이지로 리다이렉트
        return "redirect:/admin/board/manage";
    }

}
