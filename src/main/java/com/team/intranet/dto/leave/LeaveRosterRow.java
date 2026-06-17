package com.team.intranet.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 휴가 관리 명부의 한 행(직원 1명) 표시용.
 *  - granted 만 편집 대상, used/pending/remaining 은 계산값(읽기 전용).
 *  - 잔여 = 부여 - 사용.
 *  - hasRow=false 면 아직 개인 원장이 없어 "회사 기본 부여일수"를 부여로 사용 중이라는 뜻.
 *  ※ Thymeleaf 호환을 위해 record 가 아닌 @Getter 클래스로 둔다.
 */
@Getter
@AllArgsConstructor
public class LeaveRosterRow {
    private final Long memberId;
    private final String name;
    private final String deptName;
    private final String positionName;
    private final double granted;     // 부여 연차(총)
    private final double used;        // 사용 (승인된 연차)
    private final double pending;     // 신청 중 (대기/진행)
    private final double remaining;   // 잔여 = 부여 - 사용
    private final String note;        // 메모
    private final boolean hasRow;     // 개인 원장 존재 여부 (false = 회사 기본값 적용 중)
    private final java.time.LocalDate hireDate;  // 입사일(effective). null = 미상
    private final Integer positionLevel;  // 직급 레벨 (정렬용). null = 미상
    private final boolean editable;       // 현재 로그인 회원이 이 직원의 연차를 수정할 수 있는지
}
