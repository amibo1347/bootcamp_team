package com.team.intranet.enums;

import lombok.Getter;

@Getter
public enum VacationType {
    // 법정 휴가
    ANNUAL_PAID_LEAVE("연차유급휴가"),
    SICK_LEAVE("병가"),
    MATERNITY_LEAVE("출산전후휴가"),
    PATERNITY_LEAVE("배우자출산휴가"),
    MENSTRUAL_LEAVE("생리휴가"),
    FAMILY_CARE_LEAVE("가족돌봄휴가"),

    // 약정 휴가 (회사 내규)
    SPECIAL_LEAVE("경조사휴가"),
    REFRESH_LEAVE("리프레시휴가"),
    SUMMER_VACATION("하계휴가"),
    REPLACEMENT_HOLIDAY("대체휴일"),
    ETC("기타");

    private final String description;

    VacationType(String description) {
        this.description = description;
    }
}
