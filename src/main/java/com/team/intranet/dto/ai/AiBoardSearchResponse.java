package com.team.intranet.dto.ai;

import java.util.List;

import com.team.intranet.entity.Article;

/**
 * AI 비서 게시글 검색 결과 (페이지네이션). 한 페이지 = 5건.
 *  - items: 현재 페이지의 게시글 목록
 *  - page/totalPages/hasPrev/hasNext: [이전]/[다음] 버튼 제어용
 */
public record AiBoardSearchResponse(
    List<Item> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasPrev,
    boolean hasNext
) {
    public static AiBoardSearchResponse empty(int page, int size) {
        return new AiBoardSearchResponse(List.of(), Math.max(0, page), size, 0, 0, false, false);
    }

    /** 검색 결과 1건 — 카드 표시 + 이동 링크(/board/{boardId}/articles/{articleId})에 필요한 최소 정보. */
    public record Item(
        Long articleId,
        Long boardId,
        String boardName,
        String title,
        String date,     // yyyy-MM-dd
        String author     // 익명글이면 "익명"
    ) {
        public static Item from(Article a) {
            String author = a.isAnonymous() ? "익명"
                : (a.getAuthor() != null ? a.getAuthor().getName()
                    : (a.getAuthorDisplayName() != null ? a.getAuthorDisplayName() : "알 수 없음"));
            String date = a.getCreatedAt() != null ? a.getCreatedAt().toLocalDate().toString() : null;
            String boardName = a.getBoard() != null ? a.getBoard().getBoardName() : "";
            Long boardId = a.getBoard() != null ? a.getBoard().getBoardId() : null;
            return new Item(
                a.getArticleId(),
                boardId,
                boardName,
                a.getTitle() != null ? a.getTitle() : "(제목 없음)",
                date,
                author);
        }
    }
}
