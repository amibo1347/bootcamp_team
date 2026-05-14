package com.team.intranet.dto.approval;

import com.team.intranet.entity.Member;
import com.team.intranet.enums.member.Role;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * GET /api/approval/approver-candidates 응답 항목.
 * role / positionLevel / positionName 을 함께 노출해 프론트가 결재선 단계별 차등 배치 + 화면 표시에 활용한다.
 */
@Getter
@AllArgsConstructor
public class ApproverCandidate {
    private Long memberId;
    private String name;
    private String deptName;
    private String positionName;
    private Role role;
    private Integer positionLevel;

    public static ApproverCandidate from(Member m) {
        return new ApproverCandidate(
            m.getMemberId(),
            m.getName(),
            m.getDept() != null ? m.getDept().getDeptName() : null,
            m.getPosition() != null ? m.getPosition().getPositionName() : null,
            m.getRole(),
            m.getPosition() != null ? m.getPosition().getPositionLevel() : null
        );
    }
}
