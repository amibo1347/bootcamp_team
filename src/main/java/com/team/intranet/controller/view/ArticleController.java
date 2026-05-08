package com.team.intranet.controller.view;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.ui.Model;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.SessionAttribute;
import java.util.List;

import com.team.intranet.dto.ArticleDto;
import com.team.intranet.dto.BoardDto;
import com.team.intranet.session.MemberSession;
import com.team.intranet.service.BoardService;
import com.team.intranet.service.ArticleService;


@Controller
@RequestMapping("/board/{boardId}/articles")
@RequiredArgsConstructor
public class ArticleController {
    
    private final BoardService boardService;
    private final ArticleService articleService;

    // 게시글 작성
    @GetMapping("/new")
    public String createArticle(@PathVariable Long boardId, @SessionAttribute(name = "memberSession", required = false) MemberSession ms, Model model) {
        BoardDto board = boardService.findVisibleBoardById(ms, boardId);
        model.addAttribute("board", board);
        model.addAttribute("boardId", boardId);
        return "board/createPost";
    }

    // 글 상세
    @GetMapping("/{articleId}")
    public String viewArticle(@PathVariable Long boardId, @PathVariable Long articleId, @SessionAttribute(name = "memberSession", required = false) MemberSession ms, Model model) {
        BoardDto board = boardService.findVisibleBoardById(ms, boardId);
        model.addAttribute("board", board);
        model.addAttribute("articleId", articleId);
        return "board/viewPost";    
    }
}
