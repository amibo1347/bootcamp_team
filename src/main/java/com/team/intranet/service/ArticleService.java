package com.team.intranet.service;

import com.team.intranet.repository.BoardRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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

        // 게시판 존재 여부 확인
        Board board = boardRepository.findById(dto.getBoardId()).orElseThrow(() 
        -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        // 멀티 테넌시 검증
        if(!board.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }

        // 게시판 활성화 여부 확인
        if(!board.getIsActive()) {
            throw new BusinessException(ErrorCode.BOARD_INACTIVE);
        }
        
        // 게시글 작성 권한 확인
        if(!boardService.canWrite(ms, board)) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }

        // 익명 여부 확인
        boolean isAnonymous = (board.getAnonymousType() == AnonymousType.ANONYMOUS);

        // 게시글 작성자 정보 조회
        Member author = memberRepository.findById(ms.getMemberId()).orElseThrow(()
        -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        dto.setContent(HtmlSanitizer.sanitize(dto.getContent()));

        // 게시글 생성 및 저장
        Article article = Article.create(board, author, dto, isAnonymous);
        return articleRepository.save(article);
        
    }

    public List<ArticleDto> findArticlesByBoard(MemberSession ms, Long boardId){
        Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        if (!board.getCompany().getCompanyId().equals(ms.getCompanyId())) {
          throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
      }
      if (!boardService.canRead(ms, board)) {
          throw new BusinessException(ErrorCode.NO_AUTHORITY);
      }

      return articleRepository
          .findByBoard_BoardIdAndIsDeletedFalseOrderByCreatedAtDesc(boardId)
          .stream()
          .map(ArticleDto::from)
          .toList();
    }
}
