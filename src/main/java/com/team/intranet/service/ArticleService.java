package com.team.intranet.service;

import com.team.intranet.repository.BoardRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.team.intranet.entity.Article;
import com.team.intranet.dto.ArticleDto;
import com.team.intranet.enums.board.ScopeType;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.ArticleRepository;
import com.team.intranet.session.MemberSession;
import com.team.intranet.util.HtmlSanitizer;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.entity.Board;
import com.team.intranet.enums.board.AnonymousType;
import com.team.intranet.exception.BusinessException;
import java.util.List;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import com.team.intranet.entity.Member;
import com.team.intranet.repository.MemberRepository;

@Service
@RequiredArgsConstructor
public class ArticleService {
    
    // 게시글 생성 메서드
    private final BoardRepository boardRepository;
    private final BoardService boardService;
    private final ArticleRepository articleRepository;
    private final MemberRepository memberRepository;


    @Transactional
    public Article createArticle(MemberSession ms, ArticleDto dto) {

        Board board = boardService.getWritableBoard(ms, dto.getBoardId());

        // 익명 여부 확인
        boolean isAnonymous = (board.getAnonymousType() == AnonymousType.ANONYMOUS);

        // 게시글 작성자 정보 조회
        Member author = memberRepository.findById(ms.getMemberId()).orElseThrow(()
        -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 본문 정제
        dto.setContent(HtmlSanitizer.sanitize(dto.getContent()));

        // 게시글 생성 및 저장
        Article article = Article.create(board, author, dto, isAnonymous);
        return articleRepository.save(article);
        
    }

    public Page<ArticleDto> findArticlesByBoard(MemberSession ms, Long boardId, Pageable pageable){
        Board board = boardService.getReadableBoard(ms, boardId);

      return articleRepository
          .findByBoard_BoardIdAndIsDeletedFalse(boardId, pageable)
          .map(ArticleDto::from);
    }

    public ArticleDto findArticle(MemberSession ms, Long boardId, Long articleId) {
      // 게시판 read 권한 검증 (회사/활성/scope 다 처리)
      boardService.getReadableBoard(ms, boardId);

      Article article = articleRepository
          .findByArticleIdAndBoard_BoardIdAndIsDeletedFalse(articleId, boardId)
          .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

      return ArticleDto.from(article);
  }
}
