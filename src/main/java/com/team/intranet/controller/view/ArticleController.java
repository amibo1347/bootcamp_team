package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.ui.Model;
import lombok.RequiredArgsConstructor;

import com.team.intranet.dto.BoardDto;
import com.team.intranet.session.MemberSession;
import com.team.intranet.service.BoardService;

@Controller
@RequestMapping("/board")
@RequiredArgsConstructor
public class ArticleController {
    
    private final BoardService boardService;

    @GetMapping("/{id}/articles")
    public String viewBoard(@PathVariable Long id, MemberSession ms, Model model) {
    BoardDto board = boardService.findVisibleBoardById(ms, id);
    model.addAttribute("board", board);
    return "board/detail";
    }
}
