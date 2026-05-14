package com.team.intranet.dto.approval;

import com.team.intranet.enums.VacationType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * GET /api/approval/vacation-types 응답 항목.
 * name = enum 이름(서버 매칭 키), description = 한글 라벨.
 */
@Getter
@AllArgsConstructor
public class VacationTypeOption {
    private String name;
    private String description;

    public static VacationTypeOption from(VacationType t) {
        return new VacationTypeOption(t.name(), t.getDescription());
    }
}
