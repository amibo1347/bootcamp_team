package com.team.intranet.controller.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

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
import org.springframework.web.bind.annotation.RequestBody;
import com.team.intranet.dto.ArticleDto;

import java.util.List;
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

    @GetMapping("")
    @ResponseBody
    public ResponseEntity<Page<ArticleDto>> listArticles(
          @PathVariable Long boardId,
          @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
      if (ms == null) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
      return ResponseEntity.ok(articleService.findArticlesByBoard(ms, boardId, pageable));
  }

  @GetMapping("/{articleId}")
  @ResponseBody
  public ResponseEntity<ArticleDto> getArticle(
          @PathVariable Long boardId,
          @PathVariable Long articleId,
          @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
      if (ms == null) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
      return ResponseEntity.ok(articleService.findArticle(ms, boardId, articleId));
  }

      @PostMapping("/{articleId}/edit")                                                                                            
  @ResponseBody                                                                                                              
  public ResponseEntity<ArticleDto> updateArticle(
          @PathVariable Long boardId,
          @PathVariable Long articleId,
          @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
          @ModelAttribute ArticleDto dto) {
      if (ms == null) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
      return ResponseEntity.ok(articleService.updateArticle(ms, boardId, articleId, dto));
  }

    @PostMapping("/{articleId}/delete")
  @ResponseBody
  public ResponseEntity<Void> deleteArticle(
          @PathVariable Long boardId,
          @PathVariable Long articleId,
          @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
      if (ms == null) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
      articleService.deleteArticle(ms, boardId, articleId);
      return ResponseEntity.noContent().build();
  }
}
