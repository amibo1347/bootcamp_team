package com.team.intranet.enums;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public enum ApprovalStatus {
    PENDING,        // 대기 (첫 단계 결재 대기)
    IN_PROGRESS,    // 진행 (중간 단계 결재자 승인 후 다음 단계로 이동)
    APPROVED,       // 승인 (최종 단계 결재자 승인 완료)
    REJECTED,       // 반려 (어느 단계에서든 반려 발생)
    ON_HOLD         // 보류
}
