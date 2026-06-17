package com.team.intranet.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원의 "연도별" 연차 부여 원장 (B안).
 *  - 한 회원 + 한 연도 당 한 행 ((member_id, leave_year) 유니크).
 *  - grantedDays(부여)만 저장한다. "사용" 일수는 저장하지 않고
 *    VacationRequest(승인된 ANNUAL_PAID_LEAVE) 합산으로 매번 계산한다 → 결재 반려/취소 시 drift 방지.
 *    (adjustment_days 는 화면 단순화로 은퇴한 과거 컬럼 — 항상 0.)
 *  - 행이 없는 회원은 "회사 기본 부여일수(Company.defaultAnnualLeaveDays)" 를 부여로 간주한다.
 *    즉 관리자가 명시적으로 저장하기 전까지는 행을 만들지 않는다(쓰기-온-리드 회피).
 */
@Entity
@Table(name = "tbl_leave_balance",
       uniqueConstraints = @UniqueConstraint(name = "uk_leave_balance_member_year",
                                             columnNames = {"member_id", "leave_year"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_balance_id")
    private Long leaveBalanceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // 컬럼명은 leave_year — 일부 DB 에서 year 가 예약어일 수 있어 회피.
    @Column(name = "leave_year", nullable = false)
    private int year;

    /** 해당 연도 기본 부여 연차(일). 반차 0.5 단위 허용해 Double. */
    @Column(name = "granted_days", nullable = false)
    private Double grantedDays;

    /**
     * (미사용/은퇴) 과거 '조정' 컬럼. 화면을 단순화하면서 제거했고 항상 0 으로 유지한다.
     * NOT NULL 컬럼이라 엔티티에서 필드를 빼면 INSERT 가 깨지므로 컬럼만 남겨둔다.
     */
    @Column(name = "adjustment_days", nullable = false)
    private Double adjustmentDays;

    /** 조정 사유 메모(선택). */
    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static LeaveBalance create(Member member, int year, double grantedDays, String note) {
        LeaveBalance b = new LeaveBalance();
        b.member = member;
        b.year = year;
        b.grantedDays = grantedDays;
        b.adjustmentDays = 0.0; // 은퇴 컬럼 — 항상 0
        b.note = note;
        b.updatedAt = LocalDateTime.now();
        return b;
    }

    public void update(double grantedDays, String note) {
        this.grantedDays = grantedDays;
        this.adjustmentDays = 0.0;
        this.note = note;
        this.updatedAt = LocalDateTime.now();
    }
}
