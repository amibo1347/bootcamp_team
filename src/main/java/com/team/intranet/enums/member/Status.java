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
          ON_LEAVE, EnumSet.of(JOIN, BANNED, LEAVE),   // JOIN 으로 복직 허용
          // REJECT, BANNED, LEAVE는 항목 없음 = 모두 불가
          REJECT,   EnumSet.noneOf(Status.class),
          BANNED,   EnumSet.noneOf(Status.class),
          LEAVE,    EnumSet.noneOf(Status.class)
      );

      public boolean canTransitionTo(Status next) {
          return ALLOWED.getOrDefault(this, EnumSet.noneOf(Status.class)).contains(next);
      }

      public boolean isTerminal() {
          return this == REJECT || this == LEAVE || this == BANNED;
      }

      // 즉시 익명화 후 보존 기간 (스케줄러는 LEAVE/BANNED만 사용. REJECT는 즉시 row DELETE)
      public long getRetentionDays() {
          return switch (this) {
              case LEAVE  -> 730;   // 2년
              case BANNED -> 1825;  // 5년
              case REJECT -> 0;     // 즉시 삭제 (스케줄러 미사용)
              default -> throw new IllegalStateException("non-terminal status has no retention: " + this);
          };
      }
}
