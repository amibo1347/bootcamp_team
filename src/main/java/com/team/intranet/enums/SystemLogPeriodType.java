package com.team.intranet.enums;

/**
 * 시스템 로그 요약(SystemLogSummary) 의 시간 단위.
 *  - DAY: 일별 요약 (90일+ 경과 시 raw → daily 로 압축)
 *  - MONTH: 월별 요약 (365일+ 경과 시 daily → monthly 로 재압축)
 *  - QUARTER: 분기별 요약 (3년+ 경과 시 monthly → quarterly 또는 삭제)
 */
public enum SystemLogPeriodType {
    DAY,
    MONTH,
    QUARTER
}
