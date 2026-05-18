package com.team.intranet.dto;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.team.intranet.entity.AttendancePolicy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendancePolicyDto {

    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm");

    /** "HH:mm" 형식 — 프론트 진행바 계산용 */
    private String workStart;
    private String workEnd;
    private int breakMinutes;
    private int lateThresholdMin;
    /** 정책 기준 총 근무 분 (workEnd - workStart - breakMinutes). */
    private int standardWorkMin;

    public static AttendancePolicyDto fromEntity(AttendancePolicy p) {
        int totalMin = (int) java.time.Duration.between(p.getWorkStart(), p.getWorkEnd()).toMinutes();
        int standard = Math.max(0, totalMin - p.getBreakMinutes());
        return new AttendancePolicyDto(
            p.getWorkStart().format(HMS),
            p.getWorkEnd().format(HMS),
            p.getBreakMinutes(),
            p.getLateThresholdMin(),
            standard
        );
    }

    public LocalTime parseStart() { return LocalTime.parse(this.workStart, HMS); }
    public LocalTime parseEnd()   { return LocalTime.parse(this.workEnd, HMS); }
}
