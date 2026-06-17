package com.team.intranet.dto.leave;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 휴가 관리 화면 전체 모델 — 회사 기본정책(상단) + 직원 명부(하단)의 2계층을 한 번에 담는다. */
@Getter
@AllArgsConstructor
public class LeaveRosterView {
    private final int year;
    private final double companyDefault; // 회사 기본 부여일수
    private final List<LeaveRosterRow> rows;
}
