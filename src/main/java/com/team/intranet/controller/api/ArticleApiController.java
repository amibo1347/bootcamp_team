package com.team.intranet.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.SessionAttribute;
import com.team.intranet.dto.BoardDto;
import com.team.intranet.entity.Article;
import com.team.intranet.session.MemberSession;
import com.team.intranet.service.BoardService;
import com.team.intranet.service.ArticleService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.team.intranet.dto.ArticleDto;
@Controller
@RequestMapping("/api/board/{boardId}/articles")
@RequiredArgsConstructor
public class ArticleApiController {

    private final ArticleService articleService;
    private final BoardService boardService;
    
    @PostMapping("/new") 
    public ResponseEntity<?> createArticle(
            @PathVariable Long boardId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @ModelAttribute ArticleDto dto) {
        
        dto.setBoardId(boardId);
        Article saved = articleService.createArticle(ms, dto);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{articleId}")
    public String updateArticle(@PathVariable Long boardId, @PathVariable Long articleId, @RequestBody String entity) {
        
        
        return entity;
    }

    @DeleteMapping("/{articleId}")
    public String deleteArticle(@PathVariable Long boardId, @PathVariable Long articleId) {
        // 게시글 삭제 로직 수행
        return "redirect:/board/" + boardId + "/articles";
    }
}
