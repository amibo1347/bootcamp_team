package com.team.intranet.controller.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.SessionAttribute;
import com.team.intranet.dto.BoardDto;
import com.team.intranet.session.MemberSession;
import com.team.intranet.service.BoardService;


@Controller
@RequestMapping("/api/board")
@RequiredArgsConstructor
public class ArticleApiController {
    
    @PostMapping("/{boardId}/articles/new")
    public String createArticle(@PathVariable Long boardId) {
        // 게시글 생성 로직 수행
        return "redirect:/board/" + boardId + "/articles";
    }
}
