package com.team.intranet.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 시계열 추이 차트의 한 점.
 *  - 날짜별 신규 회원 / 게시글 작성 건수.
 */
@Getter
@AllArgsConstructor
public class UsageTrendPointDto {

    private final LocalDate date;
    private final long newMembers;
    private final long newArticles;
}
