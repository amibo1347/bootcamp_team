package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.ui.Model;
import lombok.RequiredArgsConstructor;

import com.team.intranet.dto.BoardDto;
import com.team.intranet.enums.board.ViewType;
import com.team.intranet.session.MemberSession;
import com.team.intranet.service.BoardService;

@Controller
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardController {
      private final BoardService boardService;

    @GetMapping("/{id}")
    public String viewBoard(@PathVariable Long id, MemberSession ms, Model model) {
        BoardDto board = boardService.findVisibleBoardById(ms, id);
        model.addAttribute("board", board);
        return switch (board.getViewType() != null ? board.getViewType() : ViewType.LIST) {
            case LIST -> "/board/list";
            case ALBUM -> "/board/album";
            case CARD -> "/board/card";
        };
    }

    @GetMapping("/{id}/posts/create")
    public String createPostPage(@PathVariable Long id, MemberSession ms, Model model) {
        BoardDto board = boardService.findVisibleBoardById(ms, id);
        model.addAttribute("board", board);
        model.addAttribute("boardId", board.getBoardId());
        return "/board/createPost";
    }
}