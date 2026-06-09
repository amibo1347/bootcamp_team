package com.team.intranet.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.team.intranet.enums.attendance.AttendanceSource;
import com.team.intranet.enums.attendance.AttendanceStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.Setter;

/**
 * 한 직원의 하루 근태 기록. (member, work_date) 유니크.
 * ※ MVP: clock_in / clock_out 만 채워지고 status/actual/overtime 은 퇴근 시점에 정책 기반으로 계산.
 */
@Entity
@Table(
    name = "tbl_attendance",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_attendance_member_date",
        columnNames = {"member_id", "work_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_id")
    private Long attendanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "clock_in")
    private LocalDateTime clockIn;

    @Column(name = "clock_out")
    private LocalDateTime clockOut;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceStatus status;

    /** 점심 제외 실근무 (분) */
    @Column(name = "actual_work_min")
    private Integer actualWorkMin;

    /** workEnd 이후 초과 근무 (분) */
    @Column(name = "overtime_min")
    private Integer overtimeMin;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private AttendanceSource source;

    public static Attendance createClockIn(Member member, LocalDate date, LocalDateTime at, AttendanceStatus status) {
        return Attendance.builder()
            .member(member)
            .workDate(date)
            .clockIn(at)
            .status(status)
            .source(AttendanceSource.CLOCK)
            .build();
    }

    public void clockOut(LocalDateTime at, int actualWorkMin, int overtimeMin, AttendanceStatus status) {
        this.clockOut = at;
        this.actualWorkMin = actualWorkMin;
        this.overtimeMin = overtimeMin;
        this.status = status;
    }

    /**
     * 퇴근 취소 — 실수로 퇴근 버튼을 눌렀을 때 출근 상태로 되돌린다.
     * clockOut/actual/overtime 을 비우고 status 는 출근 시 결정값으로 복원 (LATE 였으면 LATE 유지).
     */
    public void cancelClockOut(AttendanceStatus restoredStatus) {
        this.clockOut = null;
        this.actualWorkMin = null;
        this.overtimeMin = null;
        this.status = restoredStatus;
    }
}
