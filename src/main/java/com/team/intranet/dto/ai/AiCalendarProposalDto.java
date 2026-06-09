package com.team.intranet.dto.ai;

import java.util.List;

/**
 * AI 가 추출한 일정 액션 제안. type 에 따라 등록/수정/삭제 분기.
 *  - "calendar"        : 신규 등록
 *  - "calendar_update" : 기존 일정 수정 (calendarId 필수)
 *  - "calendar_delete" : 기존 일정 삭제 (calendarId 만 사용)
 *
 * attendeeNames: 참여자 이름 배열 (회사 동료). 서버가 이름으로 회원 매칭 후
 *  visibility=SPECIFIC + shareMemberIds 로 일정 공유 설정.
 */
public record AiCalendarProposalDto(
    String type,
    Long calendarId,
    String title,
    String description,
    String startAt,
    String endAt,
    String location,
    Boolean allDay,
    List<String> attendeeNames
) {}
