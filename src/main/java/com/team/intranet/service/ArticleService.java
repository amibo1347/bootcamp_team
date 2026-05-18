package com.team.intranet.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.dto.ArticleDto;
import com.team.intranet.dto.ArticleUnifiedTrashDto;
import com.team.intranet.entity.Article;
import com.team.intranet.entity.ArticleAttachment;
import com.team.intranet.entity.Board;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.board.AnonymousType;
import com.team.intranet.enums.board.BoardType;
import com.team.intranet.enums.member.Status;
import com.team.intranet.enums.member.SubAdminPermission;
import com.team.intranet.event.ArticleCreatedEvent;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.ArticleRepository;
import com.team.intranet.repository.AttachmentRepository;
import com.team.intranet.repository.BoardAlertPrefRepository;
import com.team.intranet.repository.BoardRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.session.MemberSession;
import com.team.intranet.util.HtmlSanitizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    // 게시글 생성 메서드
    private final BoardRepository boardRepository;
    private final BoardService boardService;
    private final ArticleRepository articleRepository;
    private final MemberRepository memberRepository;
    private final AttachmentRepository attachmentRepository;
    private final BoardAlertPrefRepository boardAlertPrefRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Article createArticle(MemberSession ms, ArticleDto dto) {

        Board board = boardService.getWritableBoard(ms, dto.getBoardId());

        // 익명 여부 확인
        boolean isAnonymous = (board.getAnonymousType() == AnonymousType.ANONYMOUS);

        // 게시글 작성자 정보 조회
        Member author = memberRepository.findById(ms.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 본문 정제
        dto.setContent(HtmlSanitizer.sanitize(dto.getContent()));

        // 게시글 생성 및 저장
        Article article = Article.create(board, author, dto, isAnonymous);
        Article saved = articleRepository.save(article);

        // 첨부파일 연결
        if (dto.getAttachmentIds() != null && !dto.getAttachmentIds().isEmpty()) {
            for (Long id : dto.getAttachmentIds()) {
                ArticleAttachment att = attachmentRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));

                // 본인이 업로드한 것 + 같은 회사 + 아직 연결 안 된 것만 허용
                if (!att.getUploader().getMemberId().equals(ms.getMemberId()))
                    continue;
                if (!att.getCompany().getCompanyId().equals(ms.getCompanyId()))
                    continue;
                if (att.getArticle() != null)
                    continue;

                att.setArticle(saved);
            }
        }
        // 알림 발송은 메인 TX 커밋 이후로 미룬다.
        //   ※ AlertService 의 @TransactionalEventListener(AFTER_COMMIT) 리스너가 수신자별로 처리.
        //   ※ 엔티티 대신 id 만 담아 트랜잭션 경계를 안전하게 넘긴다.
        Long authorId = author.getMemberId();
        List<Long> recipientIds = resolveArticleAlertRecipients(board).stream()
            .map(Member::getMemberId)
            .filter(id -> !id.equals(authorId))
            .toList();
        if (!recipientIds.isEmpty()) {
            eventPublisher.publishEvent(new ArticleCreatedEvent(saved.getArticleId(), recipientIds));
        }
        return saved;
    }

    // 게시판 타입별 수신자 결정.
    //   - NOTICE/POLICY: 회사 전 직원 강제 수신 (알림 OFF 불가 — BoardAlertPref 무시)
    //   - 그 외        : 명시적 ON 한 가입 회원만
    //   가입 승인(JOIN) 상태 회원만 대상. 휴직(ON_LEAVE)은 제외하는 정책.
    private List<Member> resolveArticleAlertRecipients(Board board) {
        BoardType type = board.getBoardType();
        boolean mandatoryAlert = (type == BoardType.NOTICE || type == BoardType.POLICY);

        if (mandatoryAlert) {
            Long companyId = board.getCompany().getCompanyId();
            return memberRepository.findByStatusAndCompanyCompanyId(Status.JOIN, companyId);
        }
        return boardAlertPrefRepository.findOptedInMembers(board, Status.JOIN);
    }

    public Page<ArticleDto> findArticlesByBoard(MemberSession ms, Long boardId, Pageable pageable) {
        Board board = boardService.getReadableBoard(ms, boardId);

        return articleRepository
                .findByBoard_BoardIdAndIsDeletedFalse(boardId, pageable)
                .map(ArticleDto::from);
    }

    @Transactional
    public ArticleDto findArticle(MemberSession ms, Long boardId, Long articleId) {
        // 게시판 read 권한 검증 (회사/활성/scope 다 처리)
        boardService.getReadableBoard(ms, boardId);

        Article article = articleRepository
                .findByArticleIdAndBoard_BoardIdAndIsDeletedFalse(articleId, boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        if (!article.isAuthor(ms.getMemberId())) {
            article.increaseViewCount();
        }

        return ArticleDto.from(article);
    }

    @Transactional(readOnly = true)
    public Page<ArticleDto> findDeletedArticles(MemberSession ms, Long boardId, Pageable pageable) {
        boardService.getReadableBoard(ms, boardId);

        Page<Article> page = ms.isAdmin()
                ? articleRepository.findByBoard_BoardIdAndIsDeletedTrue(boardId, pageable)
                : articleRepository.findByBoard_BoardIdAndAuthor_MemberIdAndIsDeletedTrue(
                        boardId, ms.getMemberId(), pageable);

        return page.map(ArticleDto::from);
    }

    /**
     * 통합 휴지통 조회.
     * ※ TRASH_MANAGEMENT 권한 보유자 → 회사 전체 삭제 글.
     * ※ 그 외 일반 회원 → 본인 작성 글 중 삭제분만 (자기 글 복구/조회 용도).
     */
    @Transactional(readOnly = true)
    public Page<ArticleUnifiedTrashDto> findDeletedArticlesForCompanyUnified(MemberSession ms, Pageable pageable) {
        Page<Article> page = ms.hasPermission(SubAdminPermission.TRASH_MANAGEMENT)
            ? articleRepository.findDeletedByCompanyId(ms.getCompanyId(), pageable)
            : articleRepository.findDeletedByCompanyIdAndAuthorId(ms.getCompanyId(), ms.getMemberId(), pageable);
        return page.map(ArticleUnifiedTrashDto::from);
    }

    @Transactional
    public ArticleDto updateArticle(MemberSession ms, Long boardId, Long articleId, ArticleDto dto) {

        // 1. 게시판 write 검증
        Board board = boardService.getWritableBoard(ms, boardId);

        // 2. 게시글 조회
        Article article = articleRepository
                .findByArticleIdAndBoard_BoardIdAndIsDeletedFalse(articleId, boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        // 3. 작성자 본인 확인
        if (!article.isAuthor(ms.getMemberId())) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }

        // 4. 본문 정제 + 업뎃
        String safeContent = HtmlSanitizer.sanitize(dto.getContent());
        article.updateInfo(dto.getTitle(), safeContent);

        // 5. 첨부 파일 변경
        if (dto.getAttachmentIds() != null && !dto.getAttachmentIds().isEmpty()) {
            for (Long id : dto.getAttachmentIds()) {
                ArticleAttachment att = attachmentRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
                if (!att.getUploader().getMemberId().equals(ms.getMemberId()))
                    continue;
                if (!att.getCompany().getCompanyId().equals(ms.getCompanyId()))
                    continue;
                if (att.getArticle() != null)
                    continue;
                att.setArticle(article);
            }
        }
        return ArticleDto.from(article);
    }

    @Transactional
    public void restoreArticle(MemberSession ms, Long boardId, Long articleId) {
        // 1. 삭제 상태인 게시글만 조회
        Article article = articleRepository
                .findByArticleIdAndBoard_BoardIdAndIsDeletedTrue(articleId, boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        // 2. 멀티테넌시 검증
        if (!article.getBoard().getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 3. 권한: 작성자 본인 OR TRASH_MANAGEMENT 보유자(ADMIN/MASTER 는 자동 통과).
        if (!article.isAuthor(ms.getMemberId()) && !ms.hasPermission(SubAdminPermission.TRASH_MANAGEMENT)) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }

        article.restore();
    }

    @Transactional
    public void permanentlyDeleteArticle(MemberSession ms, Long boardId, Long articleId) {
        // 1. 삭제 상태인 게시글만 조회 (휴지통에 있는 글만 영구삭제 가능)
        Article article = articleRepository
                .findByArticleIdAndBoard_BoardIdAndIsDeletedTrue(articleId, boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        // 2. 멀티테넌시 검증
        if (!article.getBoard().getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 3. 권한: 작성자 본인 OR TRASH_MANAGEMENT 보유자(ADMIN/MASTER 는 자동 통과).
        if (!article.isAuthor(ms.getMemberId()) && !ms.hasPermission(SubAdminPermission.TRASH_MANAGEMENT)) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }

        // 4. 첨부파일 먼저 제거 (FK 제약 회피)
        List<ArticleAttachment> attachments = attachmentRepository.findByArticle_ArticleId(articleId);
        if (!attachments.isEmpty()) {
            attachmentRepository.deleteAll(attachments);
        }

        // 5. 게시글 영구 삭제
        articleRepository.delete(article);
    }

    @Transactional
    public void deleteArticle(MemberSession ms, Long boardId, Long articleId) {
        // 1. 게시글 조회
        Article article = articleRepository
                .findByArticleIdAndBoard_BoardIdAndIsDeletedFalse(articleId, boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        // 이미 삭제된 게시글 제외
        if (article.isDeleted() == true) {
            throw new BusinessException(ErrorCode.ALEADY_DELETE_ARTICLE);
        }
        // 2. 멀티테넌시 검증 (다른 회사 게시글 차단)
        if (!article.getBoard().getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 3. 권한 확인: 작성자 본인 OR SUB_ADMIN 이상
        boolean isAuthor = article.isAuthor(ms.getMemberId());
        boolean isAdmin = ms.isAdmin();

        if (!isAuthor && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }

        article.delete();
    }
}
