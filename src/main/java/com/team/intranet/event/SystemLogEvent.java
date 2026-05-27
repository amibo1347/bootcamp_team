package com.team.intranet.event;

import com.team.intranet.enums.SystemLogAction;

/**
 * 관리 행위 발생 시 발행되는 도메인 이벤트.
 *  - 발행 시점: 서비스 메서드 내부, 본 트랜잭션 안. (AFTER_COMMIT 리스너가 별도 INSERT)
 *  - actor 는 행위자 회원 id 와 표시 이름. companyId 는 회사 격리용.
 *  - target* 는 무엇에 대한 행위인지. label 은 사람이 알아볼 스냅샷 (예: "홍길동(인사팀)").
 *  - detail 은 변경 내용 자유 텍스트. 길어지면 1000자에서 잘림.
 */
public record SystemLogEvent(
        Long companyId,
        Long actorMemberId,
        String actorName,
        SystemLogAction action,
        String targetType,
        Long targetId,
        String targetLabel,
        String detail
) {
}
