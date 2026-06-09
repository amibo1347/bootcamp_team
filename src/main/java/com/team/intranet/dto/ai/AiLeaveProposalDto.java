package com.team.intranet.dto.ai;

import java.util.List;

/**
 * AI 가 추출한 휴가 신청 제안. type = "leave".
 *  - 사용자가 채팅창 카드의 [신청] 버튼을 누르면 전자결재(VACATION 양식)로 기안된다.
 *  - 자동 제출은 하지 않는다. 카드 표시 → 사용자 확인 → confirmLeaveProposal.
 *
 * approverNames: 결재자 회원 이름 배열. 결재 순서대로(1단계 → 최종) 들어온다.
 *  서버가 이름으로 회원을 매칭해 결재선(approvalLine)을 구성한다.
 *  결재선은 사용자가 대화로 직접 정한 값만 사용한다. AI 임의 생성 금지.
 *
 * vacationType: VacationType enum 코드 추론값. 최종값은 사용자가 카드 dropdown 에서 확정한다.
 * 결재 제목은 서버가 vacationType/날짜/사유로 생성한다.
 */
public record AiLeaveProposalDto(
    String type,
    String vacationType,
    String startDate,
    String endDate,
    Double totalDays,
    String reason,
    List<String> approverNames
) {}
