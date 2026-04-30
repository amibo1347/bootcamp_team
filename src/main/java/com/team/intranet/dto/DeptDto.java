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
    private Long companyId;

    public Dept toEntity(Company company) {
        return new Dept(null, this.deptName, this.deptCode, company);
    }

}
