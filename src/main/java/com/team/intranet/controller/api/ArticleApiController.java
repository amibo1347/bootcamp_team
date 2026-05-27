package com.team.intranet.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import com.team.intranet.dto.BoardDto;
import com.team.intranet.entity.Article;
import com.team.intranet.config.AuthenticatedMember;
import com.team.intranet.session.MemberSession;
import com.team.intranet.service.BoardService;
import com.team.intranet.service.ArticleService;
import org.springframework.web.bind.annotation.RequestBody;
import com.team.intranet.dto.ArticleDto;

import java.util.List;
@RestController
@RequestMapping("/api/board/{boardId}/articles")
@RequiredArgsConstructor
public class ArticleApiController {

    private final ArticleService articleService;
    private final BoardService boardService;
    
    @PostMapping("/new") 
    public ResponseEntity<?> createArticle(
            @PathVariable Long boardId,
            @AuthenticatedMember MemberSession ms,
            @ModelAttribute ArticleDto dto) {
        
        dto.setBoardId(boardId);
        Article saved = articleService.createArticle(ms, dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("")
    public ResponseEntity<Page<ArticleDto>> listArticles(
          @PathVariable Long boardId,
          @AuthenticatedMember MemberSession ms,
          @RequestParam(required = false) String period,
          @RequestParam(required = false) String searchType,
          @RequestParam(required = false) String keyword,
        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
      return ResponseEntity.ok(articleService.findArticlesByBoard(ms, boardId, period, searchType, keyword, pageable));
  }

  @GetMapping("/trash")
  public ResponseEntity<Page<ArticleDto>> listDeletedArticles(
          @PathVariable Long boardId,
          @AuthenticatedMember MemberSession ms,
          @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
      return ResponseEntity.ok(articleService.findDeletedArticles(ms, boardId, pageable));
  }

  @GetMapping("/{articleId}")
  public ResponseEntity<ArticleDto> getArticle(
          @PathVariable Long boardId,
          @PathVariable Long articleId,
          @AuthenticatedMember MemberSession ms) {
      return ResponseEntity.ok(articleService.findArticle(ms, boardId, articleId));
  }

      @PostMapping("/{articleId}/edit")                                                                                            
  public ResponseEntity<ArticleDto> updateArticle(
          @PathVariable Long boardId,
          @PathVariable Long articleId,
          @AuthenticatedMember MemberSession ms,
          @ModelAttribute ArticleDto dto) {
      return ResponseEntity.ok(articleService.updateArticle(ms, boardId, articleId, dto));
  }

    @PostMapping("/{articleId}/delete")
  public ResponseEntity<Void> deleteArticle(
          @PathVariable Long boardId,
          @PathVariable Long articleId,
          @AuthenticatedMember MemberSession ms) {
      articleService.deleteArticle(ms, boardId, articleId);
      return ResponseEntity.noContent().build();
  }

  /** 휴지통 본문 조회 — 일반 detail API 는 deleted=true 글을 안 보여줌. 휴지통 모달 전용. */
  @GetMapping("/trash/{articleId}")
  public ResponseEntity<ArticleDto> getDeletedArticle(
          @PathVariable Long boardId,
          @PathVariable Long articleId,
          @AuthenticatedMember MemberSession ms) {
      return ResponseEntity.ok(articleService.findDeletedArticle(ms, boardId, articleId));
  }

  @PostMapping("/trash/{articleId}/restore")
  public ResponseEntity<Void> restoreArticle(
          @PathVariable Long boardId,
          @PathVariable Long articleId,
          @AuthenticatedMember MemberSession ms) {
      articleService.restoreArticle(ms, boardId, articleId);
      return ResponseEntity.noContent().build();
  }

  @PostMapping("/trash/{articleId}/delete")
  public ResponseEntity<Void> permanentlyDeleteArticle(
          @PathVariable Long boardId,
          @PathVariable Long articleId,
          @AuthenticatedMember MemberSession ms) {
      articleService.permanentlyDeleteArticle(ms, boardId, articleId);
      return ResponseEntity.noContent().build();
  }
}
