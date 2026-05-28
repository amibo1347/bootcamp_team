package com.team.intranet.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.Setter;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;

import com.team.intranet.dto.ArticleDto;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 게시글 엔티티.
 *
 * @DynamicUpdate 가 필요한 이유:
 *  - Hibernate 6 + Oracle CLOB 조합에서 @Lob 필드(content) 가 dirty checking 시
 *    값이 null 로 평가되는 케이스가 있다. (휴지통 이동 같이 다른 필드만 변경 후 commit 할 때 발생)
 *  - 정적 UPDATE 쿼리는 모든 컬럼을 SET 하므로 content=null 이 NOT NULL 제약을 위반(ORA-01407 / Hibernate PropertyValueException).
 *  - @DynamicUpdate 로 실제 변경된 필드만 SET 절에 포함시키면 content 가 update SQL 에서 빠져 문제 회피.
 */
@Entity
@Table(name = "tbl_article")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@DynamicUpdate
public class Article {

    // 변수

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "title")
    private String title;

    /**
     * 본문(CLOB).
     *  - DB 컬럼은 NOT NULL 유지(스키마 안전망).
     *  - JPA 어노테이션의 nullable=false 는 제거 — Hibernate 6 + Oracle CLOB 조합에서
     *    flush 전 Nullability check 가 fetch 된 content 값을 null 로 잘못 평가하는
     *    false-positive 가 발생해서, 다른 필드(예: isDeleted) 만 바꾼 commit 이 실패한다.
     *    (실제로 NULL 이 들어가는 코드 경로는 없으며, NOT NULL 보장은 DB constraint 에 위임.)
     */
    @Lob
    @Column(name = "CONTENT", columnDefinition = "CLOB")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board; // 게시판과의 연관관계

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Member author; // 작성자와의 연관관계 (회원 영구 삭제 시 NULL)

    @Column(name = "author_display_name")
    private String authorDisplayName; // 작성자가 탈퇴/해고/거절 후 표시할 이름

    @Column(name = "is_anonymous")
    private boolean isAnonymous; // 익명 여부

    @Column(name = "view_count")
    private Long viewCount; // 조회수

    @Column(name = "comment_count")
    private int commentCount; // 댓글 수

    @Column(name = "created_at")
    private LocalDateTime createdAt; // 생성일

    @Column(name = "updated_at")
    private String updatedAt; // 수정일

    @Column(name = "is_deleted")
    private boolean isDeleted; // 삭제 여부

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // 삭제 시점

    public static Article create(Board board, Member author, ArticleDto dto, boolean isAnonymous) {
        return Article.builder()
            .title(dto.getTitle())
            .content(dto.getContent())
            .board(board)
            .author(author)
            .isAnonymous(isAnonymous)
            .viewCount(0L)
            .createdAt(LocalDateTime.now())
            .isDeleted(false)
            .build();
    }

    public void updateInfo(String title, String content){
        this.title = title;
        this.content = content;
        this.updatedAt = LocalDateTime.now().toString();
    }

    public void increaseViewCount() {
        this.viewCount = (this.viewCount == null ? 0L : this.viewCount) + 1L;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
        this.isDeleted = true;
    }

    public void restore() {
        this.deletedAt = null;
        this.isDeleted = false;
    }

    public boolean isAuthor(Long memberId) {
        // author 는 회원 삭제 시 SET NULL 가능 → null 가드 필수 (Comment.isAuthor 와 동일 패턴).
        return this.author != null && this.author.getMemberId().equals(memberId);
    }
}
