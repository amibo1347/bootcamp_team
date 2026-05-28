package com.team.intranet.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.team.intranet.entity.Attendance;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.attendance.AttendanceStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDto {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long attendanceId;
    private Long memberId;
    private String memberName;
    private String deptName;
    private String positionName;
    /** "yyyy-MM-dd" */
    private String workDate;
    /** "HH:mm:ss" (없으면 null) */
    private String clockInTime;
    private String clockOutTime;
    /** clockIn 의 ISO datetime — 프론트 진행바가 Date 로 파싱해 사용 */
    private String clockInAt;
    private String clockOutAt;
    private String status;
    private String statusLabel;
    private Integer actualWorkMin;
    private Integer overtimeMin;

    public static AttendanceDto fromEntity(Attendance a) {
        AttendanceDto dto = new AttendanceDto();
        dto.attendanceId = a.getAttendanceId();
        if (a.getMember() != null) {
            dto.memberId = a.getMember().getMemberId();
            dto.memberName = a.getMember().getName();
            dto.deptName = a.getMember().getDept() != null ? a.getMember().getDept().getDeptName() : null;
            dto.positionName = a.getMember().getPosition() != null ? a.getMember().getPosition().getPositionName() : null;
        }
        dto.workDate = a.getWorkDate() != null ? a.getWorkDate().format(DATE) : null;
        dto.clockInTime = formatTime(a.getClockIn());
        dto.clockOutTime = formatTime(a.getClockOut());
        dto.clockInAt = formatDatetime(a.getClockIn());
        dto.clockOutAt = formatDatetime(a.getClockOut());
        dto.status = a.getStatus() != null ? a.getStatus().name() : null;
        dto.statusLabel = a.getStatus() != null ? a.getStatus().getLabel() : null;
        dto.actualWorkMin = a.getActualWorkMin();
        dto.overtimeMin = a.getOvertimeMin();
        return dto;
    }

    private static String formatTime(LocalDateTime t) {
        return t == null ? null : t.format(TIME);
    }

    private static String formatDatetime(LocalDateTime t) {
        return t == null ? null : t.format(DATETIME);
    }

    /**
     * 휴직 가상 row — DB에 attendance row 없이 ON_LEAVE 상태만 의미적으로 표시.
     * 하루 단위 (시간 정보 없음).
     */
    public static AttendanceDto onLeavePlaceholder(Member member, LocalDate date) {
        AttendanceDto dto = new AttendanceDto();
        dto.attendanceId = null; // DB row 없음 — 프론트에서 수정/취소 못 함
        if (member != null) {
            dto.memberId = member.getMemberId();
            dto.memberName = member.getName();
            dto.deptName = member.getDept() != null ? member.getDept().getDeptName() : null;
            dto.positionName = member.getPosition() != null ? member.getPosition().getPositionName() : null;
        }
        dto.workDate = date != null ? date.format(DATE) : null;
        dto.status = AttendanceStatus.ON_LEAVE.name();
        dto.statusLabel = AttendanceStatus.ON_LEAVE.getLabel();
        return dto;
    }
}
