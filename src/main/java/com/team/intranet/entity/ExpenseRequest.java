package com.team.intranet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * 지출결의서 본문 (formCode = "EXPENSE").
 * Approval 과 1:1 — VacationRequest 와 동일 패턴.
 */
@Entity
@Table(name = "tbl_expense_request")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ExpenseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_request_id")
    private Long expenseRequestId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_id", nullable = false, unique = true)
    private Approval approval; // 어느 결재 문서의 본문인지 (1:1)

    @Column(name = "amount", nullable = false)
    private Long amount; // 지출 금액 (원 단위)

    @Column(name = "category", nullable = false, length = 50)
    private String category; // 지출 분류 (예: 식대, 교통비, 출장비, 회식)

    @Column(name = "spent_at", nullable = false)
    private LocalDate spentAt; // 지출 일자

    @Lob
    @Column(name = "description", columnDefinition = "CLOB")
    private String description; // 지출 상세 내역
}
