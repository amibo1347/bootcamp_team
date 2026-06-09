package com.team.intranet.dto;

import com.team.intranet.entity.Member;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 새 채팅 화면의 회원 검색 결과 1행. 이름/부서/직급 + 프로필 이미지 URL. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatPeerDto {

    private Long memberId;
    private String name;
    private String deptName;
    private String positionName;
    /** 프로필 이미지 다운로드 URL — 없으면 null. */
    private String profileImageUrl;

    public static ChatPeerDto fromEntity(Member m) {
        ChatPeerDto dto = new ChatPeerDto();
        dto.memberId = m.getMemberId();
        dto.name = m.getName();
        dto.deptName = m.getDept() != null ? m.getDept().getDeptName() : null;
        dto.positionName = m.getPosition() != null ? m.getPosition().getPositionName() : null;
        // 헤더와 동일한 기존 엔드포인트 재사용 — LAZY byte[] 로딩 이슈 회피.
        // 등록된 이미지가 없으면 404 → 프론트가 onerror 로 이니셜 동그라미로 폴백한다.
        dto.profileImageUrl = "/api/member/" + m.getMemberId() + "/profileImg";
        return dto;
    }
}
