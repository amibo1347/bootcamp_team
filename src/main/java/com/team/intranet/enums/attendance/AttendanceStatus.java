package com.team.intranet.enums.attendance;

/**
 * 한 직원의 하루 근태 상태.
 *  - NORMAL: 정상 출퇴근
 *  - LATE: 지각 (정책의 work_start + late_threshold_min 초과 후 출근)
 *  - EARLY_LEAVE: 조퇴 (정책 work_end 이전 퇴근)
 *  - ABSENT: 결근 (출근 기록 없음)
 *  - VACATION: 휴가 (전자결재 휴가 승인 자동 반영용 — MVP 에선 직접 사용 안 함)
 *  - HOLIDAY: 휴일/공휴일
 *  - ON_LEAVE: 휴직 중
 */
public enum AttendanceStatus {
    NORMAL("정상"),
    LATE("지각"),
    EARLY_LEAVE("조퇴"),
    ABSENT("결근"),
    VACATION("휴가"),
    HOLIDAY("휴일"),
    ON_LEAVE("휴직");

    private final String label;

    AttendanceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
