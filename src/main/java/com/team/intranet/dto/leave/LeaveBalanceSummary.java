package com.team.intranet.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 직원 본인 1명의 연차 요약(읽기 전용) — 대시보드 배지 / AI 컨텍스트 공용.
 *  - 잔여 = 부여 - 사용. (신청중은 아직 미차감, 참고용)
 */
@Getter
@AllArgsConstructor
public class LeaveBalanceSummary {
    private final int year;
    private final double granted;     // 부여
    private final double used;        // 사용 (승인된 연차)
    private final double pending;     // 신청 중 (대기/진행)
    private final double remaining;   // 잔여
}
