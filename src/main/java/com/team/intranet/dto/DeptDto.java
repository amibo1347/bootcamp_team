package com.team.intranet.dto;

import com.team.intranet.entity.Company;
import com.team.intranet.entity.Dept;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeptDto {
    private Long deptId;
    private String deptName;
    private String deptCode;
    private Company company;

    public Dept toEntity(){
        return new Dept(null, deptName, deptCode, company);
    }
}
