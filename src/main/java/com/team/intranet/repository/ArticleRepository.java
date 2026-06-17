package com.team.intranet.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.Article;
import com.team.intranet.enums.board.BoardType;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    List<Article> findByBoard_BoardIdAndIsDeletedFalseOrderByCreatedAtDesc(Long boardId);

     @Query("""
      SELECT a FROM Article a
      JOIN FETCH a.board
      LEFT JOIN FETCH a.author
      WHERE a.board.boardId = :boardId AND a.isDeleted = false
  """)
    Page<Article> findByBoard_BoardIdAndIsDeletedFalse(Long boardId, Pageable pageable);

    /**
     * 게시글 검색 — 기간 + 검색 대상별 키워드 조건. 빈 값(null)은 해당 조건을 건너뛴다.
     *  - :from        null 이면 기간 무제한
     *  - :keyword     null/공백이면 키워드 조건 미적용
     *  - :searchType  "ALL"(제목+내용), "TITLE"(제목), "AUTHOR"(작성자명) — 익명글의 작성자 검색은 제외
     *
     * 주의: a.content 는 @Lob (CLOB) 이라 Hibernate 6 에서 LOWER() 적용 불가.
     *       → 본문 검색은 case-sensitive LIKE 로 처리 (한글은 영향 없음, 영문은 입력 그대로 매칭).
     *       제목/작성자는 LOWER 양쪽 적용으로 case-insensitive 유지.
     */
    @Query("""
        SELECT a FROM Article a
        JOIN FETCH a.board
        LEFT JOIN FETCH a.author au
        WHERE a.board.boardId = :boardId
          AND a.isDeleted = false
          AND (:from IS NULL OR a.createdAt >= :from)
          AND (
                :keyword IS NULL
             OR (:searchType = 'ALL'    AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                          OR a.content LIKE CONCAT('%', :keyword, '%')))
             OR (:searchType = 'TITLE'  AND LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
             OR (:searchType = 'AUTHOR' AND a.isAnonymous = false
                                        AND au IS NOT NULL
                                        AND LOWER(au.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
          )
        """)
    Page<Article> searchByBoard(
            @Param("boardId") Long boardId,
            @Param("from") LocalDateTime from,
            @Param("searchType") String searchType,
            @Param("keyword") String keyword,
            Pageable pageable);

     @Query("""
      SELECT a FROM Article a
      JOIN FETCH a.board
      LEFT JOIN FETCH a.author
      WHERE a.board.boardId = :boardId AND a.isDeleted = true
  """)
  Page<Article> findByBoard_BoardIdAndIsDeletedTrue(Long boardId, Pageable pageable);

    /**
     * AI 비서 게시글 검색 — 접근 가능한 여러 게시판을 한 번에. 빈 조건(null)은 건너뜀.
     *  - :boardIds  검색 대상 게시판 ID 목록 (비어 있으면 호출하지 말 것 — IN () 무효)
     *  - :from/:to  작성일 범위 [from, to). null 이면 해당 경계 무제한
     *  - :keyword   제목 OR 본문 부분일치 (CLOB 본문은 case-sensitive)
     *  - :author    작성자명 부분일치 (익명글 제외)
     * 정렬은 Pageable(Sort) 로 전달.
     */
    @Query(value = """
        SELECT a FROM Article a
        JOIN FETCH a.board b
        LEFT JOIN FETCH a.author au
        WHERE a.isDeleted = false
          AND b.boardId IN :boardIds
          AND (:from IS NULL OR a.createdAt >= :from)
          AND (:to   IS NULL OR a.createdAt <  :to)
          AND (:keyword IS NULL
               OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR a.content LIKE CONCAT('%', :keyword, '%'))
          AND (:author IS NULL
               OR (a.isAnonymous = false AND au IS NOT NULL
                   AND LOWER(au.name) LIKE LOWER(CONCAT('%', :author, '%'))))
        """,
        countQuery = """
        SELECT COUNT(a) FROM Article a LEFT JOIN a.author au
        WHERE a.isDeleted = false
          AND a.board.boardId IN :boardIds
          AND (:from IS NULL OR a.createdAt >= :from)
          AND (:to   IS NULL OR a.createdAt <  :to)
          AND (:keyword IS NULL
               OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR a.content LIKE CONCAT('%', :keyword, '%'))
          AND (:author IS NULL
               OR (a.isAnonymous = false AND au IS NOT NULL
                   AND LOWER(au.name) LIKE LOWER(CONCAT('%', :author, '%'))))
        """)
    Page<Article> aiSearch(
            @Param("boardIds") List<Long> boardIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("keyword") String keyword,
            @Param("author") String author,
            Pageable pageable);

    Page<Article> findByBoard_BoardIdAndAuthor_MemberIdAndIsDeletedTrue(Long boardId, Long memberId, Pageable pageable);

    Optional<Article> findByArticleIdAndBoard_BoardIdAndIsDeletedFalse(Long articleId, Long boardId);

    Optional<Article> findByArticleIdAndBoard_BoardIdAndIsDeletedTrue(Long articleId, Long boardId);

    List<Article> findByIsDeletedTrueAndBoard_BoardTypeAndDeletedAtBefore(
        BoardType boardType, LocalDateTime threshoId);
    
    /** 회사(테넌트) 단위 소프트 삭제 글 페이지 — board·author 패치 포함 */
    @Query("""
        SELECT a FROM Article a
        JOIN FETCH a.board b
        LEFT JOIN FETCH a.author
        WHERE b.company.companyId = :companyId AND a.isDeleted = true
        """)
    Page<Article> findDeletedByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    /**
     * 회사 단위 + 작성자(=본인) 소프트 삭제 글 페이지.
     * ※ 통합 휴지통에서 TRASH_MANAGEMENT 권한이 없는 일반 사용자에게 본인 작성 글만 보여주기 위한 쿼리.
     */
    @Query("""
        SELECT a FROM Article a
        JOIN FETCH a.board b
        LEFT JOIN FETCH a.author
        WHERE b.company.companyId = :companyId
          AND a.author.memberId = :memberId
          AND a.isDeleted = true
        """)
    Page<Article> findDeletedByCompanyIdAndAuthorId(
        @Param("companyId") Long companyId,
        @Param("memberId") Long memberId,
        Pageable pageable);

    /**
     * 통합 휴지통 검색 (회사 전체 권한자) — searchByBoard 와 동일 분기 규칙.
     * CLOB 인 content 는 LOWER 적용 불가 → 본문 검색만 case-sensitive LIKE.
     */
    @Query("""
        SELECT a FROM Article a
        JOIN FETCH a.board b
        LEFT JOIN FETCH a.author au
        WHERE b.company.companyId = :companyId
          AND a.isDeleted = true
          AND (:from IS NULL OR a.createdAt >= :from)
          AND (
                :keyword IS NULL
             OR (:searchType = 'ALL'    AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                          OR a.content LIKE CONCAT('%', :keyword, '%')))
             OR (:searchType = 'TITLE'  AND LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
             OR (:searchType = 'AUTHOR' AND a.isAnonymous = false
                                        AND au IS NOT NULL
                                        AND LOWER(au.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
          )
        """)
    Page<Article> searchDeletedByCompanyId(
        @Param("companyId") Long companyId,
        @Param("from") LocalDateTime from,
        @Param("searchType") String searchType,
        @Param("keyword") String keyword,
        Pageable pageable);

    /**
     * 통합 휴지통 검색 (본인 작성 글로 제한) — TRASH_MANAGEMENT 권한 없는 회원용.
     */
    @Query("""
        SELECT a FROM Article a
        JOIN FETCH a.board b
        LEFT JOIN FETCH a.author au
        WHERE b.company.companyId = :companyId
          AND a.author.memberId = :memberId
          AND a.isDeleted = true
          AND (:from IS NULL OR a.createdAt >= :from)
          AND (
                :keyword IS NULL
             OR (:searchType = 'ALL'    AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                          OR a.content LIKE CONCAT('%', :keyword, '%')))
             OR (:searchType = 'TITLE'  AND LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
             OR (:searchType = 'AUTHOR' AND a.isAnonymous = false
                                        AND au IS NOT NULL
                                        AND LOWER(au.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
          )
        """)
    Page<Article> searchDeletedByCompanyIdAndAuthorId(
        @Param("companyId") Long companyId,
        @Param("memberId") Long memberId,
        @Param("from") LocalDateTime from,
        @Param("searchType") String searchType,
        @Param("keyword") String keyword,
        Pageable pageable);

    /** 회원이 종료 상태(LEAVE/BANNED)로 전이될 때, 작성한 모든 글의 표시명을 고정. */
    @Modifying
    @Query("UPDATE Article a SET a.authorDisplayName = :name WHERE a.author.memberId = :memberId AND a.authorDisplayName IS NULL")
    int markAuthorDisplayName(@Param("memberId") Long memberId, @Param("name") String name);

    /** 회사별 게시글 수 (삭제 제외) — MASTER 사용량 대시보드. */
    @Query("SELECT COUNT(a) FROM Article a WHERE a.board.company.companyId = :companyId AND a.isDeleted = false")
    long countByCompanyId(@Param("companyId") Long companyId);

    /** 회사별 게시글 수 일괄 (삭제 제외) — N+1 회피. [companyId, count]. */
    @Query("SELECT a.board.company.companyId, COUNT(a) FROM Article a WHERE a.isDeleted = false GROUP BY a.board.company.companyId")
    List<Object[]> countArticlesPerCompany();

    /** 전체 게시글 수 (삭제 제외) — KPI. */
    @Query("SELECT COUNT(a) FROM Article a WHERE a.isDeleted = false")
    long countAllArticles();

    /**
     * since 이후 작성된 게시글들의 createdAt 목록 (삭제 글 제외) — 시계열 차트용.
     *  - 일별 그룹은 호출 측이 Java 에서 처리.
     */
    @Query("SELECT a.createdAt FROM Article a WHERE a.createdAt >= :since AND a.isDeleted = false")
    List<LocalDateTime> findCreatedAtSinceNotDeleted(@Param("since") LocalDateTime since);

}
