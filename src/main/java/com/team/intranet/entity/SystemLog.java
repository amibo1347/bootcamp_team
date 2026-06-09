package com.team.intranet.entity;

import java.time.LocalDateTime;

import com.team.intranet.enums.SystemLogAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회사 시스템 로그(raw) — 관리 행위 한 건당 1 row.
 *  - 회사별 격리. 인덱스 (company_id, created_at DESC) — ADMIN 페이지 페이징·기간 조회 핵심.
 *  - 행위 주체는 actor 회원 + 이름 스냅샷(회원이 퇴사·이름 변경되어도 감사 시점 이름 유지).
 *  - 대상은 targetType + targetId + targetLabel 스냅샷 형태 (FK 없음 — 삭제 후에도 추적 가능).
 *  - 90일 경과 후엔 SystemLogSummary(DAY) 로 압축되며 raw row 는 삭제된다.
 */
@Entity
@Table(name = "tbl_system_log",
    indexes = {
        @Index(name = "idx_system_log_company_created", columnList = "company_id, created_at"),
        @Index(name = "idx_system_log_company_actor", columnList = "company_id, actor_member_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    /** 회사 격리 — ADMIN 페이지는 본인 회사 로그만 조회. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** 행위 주체 — 회원 퇴사 후에도 row 자체는 유지되므로 LAZY + nullable 허용. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_member_id")
    private Member actor;

    /** 행위 시점의 actor 이름 스냅샷 — 회원이 사라지거나 이름이 바뀌어도 감사용으로 보존. */
    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "action", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SystemLogAction action;

    /** 대상 도메인 분류 — "MEMBER" / "BOARD" / "ARTICLE" / "FORM_TEMPLATE" 등. */
    @Column(name = "target_type", length = 40)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    /** 대상의 사람이 알아볼 이름/제목 스냅샷 — "홍길동(인사팀)" / "공지사항 게시판" 등. */
    @Column(name = "target_label", length = 200)
    private String targetLabel;

    /** 자유 텍스트 상세 — "직급: 사원 → 대리" 같은 변경 내용. AI 요약에 그대로 인용된다. */
    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
