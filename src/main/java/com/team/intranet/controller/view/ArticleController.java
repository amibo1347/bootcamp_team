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

    @GetMapping("/{articleId}/edit")
  public String editArticle(
          @PathVariable Long boardId,
          @PathVariable Long articleId,
          @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
          Model model) {

      BoardDto board = boardService.findVisibleBoardById(ms, boardId);
      ArticleDto article = articleService.findArticle(ms, boardId, articleId);

      // 작성자 본인만 폼 진입 (UX — 백엔드에서 또 검증되니 이중 보호)
      if (!article.getAuthorId().equals(ms.getMemberId())) {
          return "redirect:/board/" + boardId + "/articles/" + articleId;
      }

      model.addAttribute("board", board);
      model.addAttribute("boardId", boardId);
      model.addAttribute("article", article);
      return "board/editPost";   // 또는 createPost를 mode=edit으로 재활용
  }
}
