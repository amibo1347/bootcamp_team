package com.team.intranet.dto.approval;

import com.team.intranet.entity.ApprovalLine;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.ApprovalStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 결재선 한 단계(level) 정보. 내 결재함 행의 드롭다운에서 단계별 결재자 표시용.
 */
@Getter
@AllArgsConstructor
public class ApprovalLineRow {

    private Integer level;
    private Long memberId;
    private String name;
    private String deptName;
    private String positionName;
    private ApprovalStatus status; // 이 단계의 처리 상태 (PENDING/APPROVED/REJECTED/ON_HOLD)

    public static ApprovalLineRow from(ApprovalLine line) {
        Member m = line.getApprover();
        return new ApprovalLineRow(
            line.getLevel(),
            m == null ? null : m.getMemberId(),
            m == null ? null : m.getName(),
            (m != null && m.getDept() != null) ? m.getDept().getDeptName() : null,
            (m != null && m.getPosition() != null) ? m.getPosition().getPositionName() : null,
            line.getStatus()
        );
    }
}
