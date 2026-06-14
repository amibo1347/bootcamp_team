package com.team.intranet.service.ai;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.dto.ai.AiBoardSearchResponse;
import com.team.intranet.entity.Article;
import com.team.intranet.entity.Board;
import com.team.intranet.repository.ArticleRepository;
import com.team.intranet.repository.BoardRepository;
import com.team.intranet.service.BoardService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * AI 비서 게시글 검색 — 채팅 카드의 [이전]/[다음] 페이지네이션이 호출.
 *  - 회사 격리 + 게시판 ACL(canRead) 적용: 회원이 인트라넷에서 못 보는 게시판은 검색 대상에서 제외.
 *  - AI 활성(isAiUse) 게시판만 검색 (AI 컨텍스트 노출 정책과 일치).
 *  - 한 페이지 5건, 작성일 최신순.
 */
@Service
@RequiredArgsConstructor
public class AiBoardSearchService {

    /** 한 페이지 게시글 수. */
    public static final int PAGE_SIZE = 5;

    private final BoardRepository boardRepository;
    private final ArticleRepository articleRepository;
    private final BoardService boardService;

    /**
     * @param boardId  특정 게시판으로 한정 (null 이면 접근 가능한 모든 게시판)
     * @param startDate/endDate  작성일 범위 (둘 다 포함). null 이면 해당 경계 무제한
     * @param page     0-base 페이지 번호
     */
    @Transactional(readOnly = true)
    public AiBoardSearchResponse search(MemberSession ms, String keyword, String author,
                                        Long boardId, LocalDate startDate, LocalDate endDate, int page) {
        int safePage = Math.max(0, page);

        // 회사 + AI 활성 + 활성 + 읽기 권한(canRead) 통과한 게시판만 검색 대상.
        List<Board> accessible = boardRepository
            .findAllByCompany_CompanyIdAndIsAiUseTrueAndIsActiveTrue(ms.getCompanyId()).stream()
            .filter(b -> boardService.canRead(ms, b))
            .toList();

        List<Long> boardIds;
        if (boardId != null) {
            boolean ok = accessible.stream().anyMatch(b -> b.getBoardId().equals(boardId));
            boardIds = ok ? List.of(boardId) : List.of();
        } else {
            boardIds = accessible.stream().map(Board::getBoardId).toList();
        }
        if (boardIds.isEmpty()) {
            return AiBoardSearchResponse.empty(safePage, PAGE_SIZE);
        }

        LocalDateTime from = startDate != null ? startDate.atStartOfDay() : null;
        // endDate 는 그날 끝까지 포함 → 다음날 0시 미만(exclusive).
        LocalDateTime to = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;
        String kw = blankToNull(keyword);
        String au = blankToNull(author);

        Pageable pageable = PageRequest.of(safePage, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Article> result = articleRepository.aiSearch(boardIds, from, to, kw, au, pageable);

        List<AiBoardSearchResponse.Item> items = result.getContent().stream()
            .map(AiBoardSearchResponse.Item::from)
            .toList();

        return new AiBoardSearchResponse(
            items,
            result.getNumber(),
            PAGE_SIZE,
            result.getTotalElements(),
            result.getTotalPages(),
            result.hasPrevious(),
            result.hasNext());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
