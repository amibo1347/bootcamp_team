package com.team.intranet.enums;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public enum ApprovalStatus {
    PENDING,    // 대기중
    APPROVED,   // 승인
    REJECTED    // 반려
}
