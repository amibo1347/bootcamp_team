package com.team.intranet.dto;

import com.team.intranet.entity.Company;
import com.team.intranet.entity.Position;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.team.intranet.enums.member.Role;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositionDto {
    private Long positionId;
    private String positionName;
    private Company company;
    private int positionLevel;

    @JsonProperty("isAdmin")
    private boolean isAdmin;

    public Position toEntity(){
        Role role = isAdmin ? Role.SUB_ADMIN : Role.USER;
        return new Position(null, positionName, company, positionLevel, role);
    }

    public static PositionDto fromEntity(Position position) {
        boolean isAdmin = position.getRole() == Role.SUB_ADMIN;
        return new PositionDto(position.getPositionId(), position.getPositionName(), position.getCompany(), position.getPositionLevel(), isAdmin);
    }
}
