package com.team.intranet.service.ai;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.entity.Article;
import com.team.intranet.entity.Board;
import com.team.intranet.repository.ArticleRepository;
import com.team.intranet.repository.BoardRepository;
import com.team.intranet.service.BoardService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * AI 비서가 참고할 게시판 컨텍스트를 system prompt 용 텍스트로 빌드한다.
 *  - 회사 격리: 로그인 회원 회사의 게시판만.
 *  - 활성 + isAiUse=true 게시판만.
 *  - 게시판 ACL 적용: BoardService.canRead(ms, board) 로 RESTRICTED 게시판의 부서/직급 필터 검사.
 *    → 회원이 인트라넷 화면에서 못 보는 게시판은 AI 컨텍스트에도 포함하지 않는다.
 *  - 각 게시판당 최근 N개 (제목 + 본문 앞 부분).
 *  - LLM 토큰 절약을 위해 본문은 잘라서 첨부.
 */
@Service
@RequiredArgsConstructor
public class AiBoardContextService {

    /** 게시판당 최근 N개 게시글까지만 컨텍스트에 포함. */
    private static final int PER_BOARD_LIMIT = 5;
    /** 본문 미리보기 글자 수 (HTML 제거 후). */
    private static final int CONTENT_PREVIEW_LEN = 220;

    private final BoardRepository boardRepository;
    private final ArticleRepository articleRepository;
    private final BoardService boardService;

    /**
     * 로그인 회원이 읽을 수 있는 AI 활성 게시판 + 최근 게시글 목록을 system prompt 용 문자열로 빌드.
     * AI 활성 게시판이 0개 또는 권한 필터 후 모두 제외되면 명시적으로 알림 문구만 반환.
     *
     * @param ms 로그인 회원 세션 — 회사 격리 + 게시판 ACL(BoardService.canRead) 검사에 사용
     */
    @Transactional(readOnly = true)
    public String buildContext(MemberSession ms) {
        Long companyId = ms.getCompanyId();
        // 회사 + 활성 + isAiUse 필터링은 기존 그대로, 거기서 추가로 BoardService.canRead 로 ACL 검사.
        // → 회원의 직급/부서로 RESTRICTED 게시판의 BoardScopeRule(READ) 매칭 여부를 검증.
        //   회원이 인트라넷 화면에서 못 보는 게시판은 AI 컨텍스트에도 포함되지 않는다.
        List<Board> aiBoards =
            boardRepository.findAllByCompany_CompanyIdAndIsAiUseTrueAndIsActiveTrue(companyId).stream()
                .filter(b -> boardService.canRead(ms, b))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n[참고 가능 게시판 데이터]\n");

        if (aiBoards.isEmpty()) {
            sb.append("(이 회사에 AI 가 접근 가능한 게시판이 없습니다. 게시판 관련 질문에는 ")
              .append("\"현재 AI 가 접근 가능한 게시판이 없습니다.\" 라고만 답하세요.)\n");
            return sb.toString();
        }

        sb.append("아래 게시판 글만 답변 근거로 사용하세요. ")
          .append("나열되지 않은 게시판은 \"권한이 없어 확인할 수 없다\"고 답하세요.\n");

        for (Board b : aiBoards) {
            sb.append("\n## ").append(b.getBoardName())
              .append(" (게시판 종류: ").append(b.getBoardType()).append(")\n");

            List<Article> recent = articleRepository
                .findByBoard_BoardIdAndIsDeletedFalse(
                    b.getBoardId(),
                    PageRequest.of(0, PER_BOARD_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"))
                ).getContent();

            if (recent.isEmpty()) {
                sb.append("  - (게시글 없음)\n");
                continue;
            }
            int idx = 1;
            for (Article a : recent) {
                String date = a.getCreatedAt() != null ? a.getCreatedAt().toLocalDate().toString() : "?";
                String title = a.getTitle() != null ? a.getTitle() : "(제목 없음)";
                sb.append("  ").append(idx++).append(". [").append(date).append("] ").append(title).append("\n");
                String preview = truncate(stripHtml(a.getContent()), CONTENT_PREVIEW_LEN);
                if (!preview.isBlank()) {
                    sb.append("     본문: ").append(preview).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private static String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", " ")     // 태그 제거
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
