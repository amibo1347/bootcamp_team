package com.team.intranet.enums.member;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum Status {
    WAIT,       // 가입 대기중
    JOIN,       // 가입 승인
    LEAVE,      // 퇴사
    REJECT,     // 가입 거절(반려)
    ON_LEAVE,    // 휴직
    BANNED;       // 해고
    
    // 허용되는 전이 (지연 초기화로 순환 참조 회피)
      private static final Map<Status, Set<Status>> ALLOWED = Map.of(
          WAIT,     EnumSet.of(JOIN, REJECT),
          JOIN,     EnumSet.of(BANNED, LEAVE, ON_LEAVE),
          ON_LEAVE, EnumSet.of(BANNED, LEAVE),   // 복직 허용 시 JOIN 추가
          // REJECT, BANNED, LEAVE는 항목 없음 = 모두 불가
          REJECT,   EnumSet.noneOf(Status.class),
          BANNED,   EnumSet.noneOf(Status.class),
          LEAVE,    EnumSet.noneOf(Status.class)
      );

      public boolean canTransitionTo(Status next) {
          return ALLOWED.getOrDefault(this, EnumSet.noneOf(Status.class)).contains(next);
      }
}
