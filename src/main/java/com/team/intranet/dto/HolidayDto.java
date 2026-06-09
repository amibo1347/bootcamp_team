package com.team.intranet.dto;

/**
 * 캘린더 일자 셀에 인라인 라벨로 표시할 휴일 한 건.
 *  - date: ISO 양력 날짜 "YYYY-MM-DD"
 *  - name: 휴일 이름 (예: "신정", "부처님오신날", "대체공휴일")
 *  - kind: "PUBLIC" (법정 공휴일 — 빨강) | "OBSERVANCE" (기념일 — 호박색)
 *  ※ 짧은 표시명(shortTitle) 매핑은 프론트엔드(calendar-holidays.js) 가 담당한다.
 */
public record HolidayDto(String date, String name, String kind) {
}
