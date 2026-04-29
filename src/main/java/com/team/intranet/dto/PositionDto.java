package com.team.intranet.dto;

import com.team.intranet.entity.Company;
import com.team.intranet.entity.Position;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositionDto {
    private Long positionId;
    private String positionName;
    private Company company;
    private int positionLevel;

    public Position toEntity(){
        return new Position(null, positionName, company, positionLevel);
    }
}
