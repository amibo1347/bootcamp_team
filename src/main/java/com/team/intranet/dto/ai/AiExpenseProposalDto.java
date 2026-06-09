package com.team.intranet.dto.ai;

import java.util.List;

/**
 * AI 가 추출한 지출결의서 신청 제안. type = "expense".
 *  - 사용자가 채팅창 카드의 [신청] 버튼을 누르면 전자결재(EXPENSE 양식)로 기안된다.
 *  - 자동 제출은 하지 않는다. 카드 표시 → 사용자 확인 → confirmExpenseProposal.
 *
 * approverNames: 결재자 회원 이름 배열. 결재 순서대로(1단계 → 최종) 들어온다.
 *  서버가 이름으로 회원을 매칭해 결재선(approvalLine)을 구성한다. AI 임의 생성 금지.
 *
 * amount: 지출 금액(원). category: 분류(식대/교통비 등). spentAt: 지출일 "YYYY-MM-DD".
 * description: 상세 내역(선택). 결재 제목은 서버가 분류/금액으로 생성한다.
 */
public record AiExpenseProposalDto(
    String type,
    Long amount,
    String category,
    String spentAt,
    String description,
    List<String> approverNames
) {}
