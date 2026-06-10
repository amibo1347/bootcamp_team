package com.team.intranet.enums;

import lombok.Getter;

/**
 * 반차 구분.
 *  - null(미설정) = 종일 휴가.
 *  - AM/PM = 반차(0.5일). 시작일 하루에만 적용된다.
 */
@Getter
public enum HalfDayPeriod {
    AM("오전 반차"),
    PM("오후 반차");

    private final String description;

    HalfDayPeriod(String description) {
        this.description = description;
    }

    /** 자유 텍스트("AM"/"PM"/"오전"/"오후")를 enum 으로. 매칭 실패·blank 면 null(=종일). */
    public static HalfDayPeriod parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toUpperCase();
        if (v.equals("AM") || v.contains("오전")) return AM;
        if (v.equals("PM") || v.contains("오후")) return PM;
        return null;
    }
}
