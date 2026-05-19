package com.team.intranet.enums.attendance;

/**
 * 근태 기록이 어디서 생성됐는지. 트러블슈팅 / 감사용.
 *  - CLOCK: 직원이 출퇴근 버튼으로 직접 찍음
 *  - VACATION_APPROVAL: 휴가 결재 승인으로 자동 생성 (v2+)
 *  - CORRECTION: 정정 요청 승인으로 변경 (v2+)
 *  - ADMIN: 관리자 직접 입력 (v3+)
 */
public enum AttendanceSource {
    CLOCK,
    VACATION_APPROVAL,
    CORRECTION,
    ADMIN
}
