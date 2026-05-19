package com.team.intranet.entity;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회사별 근태 정책. 회사당 1행.
 * ※ 없으면 AttendanceService.getOrCreatePolicy() 가 lazy 디폴트(09:00-18:00, 60분 break, 10분 지각)를 만들어준다.
 */
@Entity
@Table(name = "tbl_attendance_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendancePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Long policyId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(name = "work_start", nullable = false)
    private LocalTime workStart;

    @Column(name = "work_end", nullable = false)
    private LocalTime workEnd;

    /** 휴게시간(분). 실근무 계산 시 일괄 차감. */
    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    /** workStart + 이 분 이후 출근하면 LATE 로 마킹. */
    @Column(name = "late_threshold_min", nullable = false)
    private int lateThresholdMin;

    public static AttendancePolicy defaultFor(Company company) {
        return AttendancePolicy.builder()
            .company(company)
            .workStart(LocalTime.of(9, 0))
            .workEnd(LocalTime.of(18, 0))
            .breakMinutes(60)
            .lateThresholdMin(10)
            .build();
    }

    public void update(LocalTime workStart, LocalTime workEnd, int breakMinutes, int lateThresholdMin) {
        this.workStart = workStart;
        this.workEnd = workEnd;
        this.breakMinutes = breakMinutes;
        this.lateThresholdMin = lateThresholdMin;
    }
}
