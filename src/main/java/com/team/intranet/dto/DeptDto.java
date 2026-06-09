package com.team.intranet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    /** 시스템 보호 부서 여부 — UI에서 삭제를 잠그는 데 사용. */
    @JsonProperty("isSystem")
    private boolean isSystem;

    public Dept toEntity(Company company) {
        // 일반 부서 생성 경로 — isSystem 은 항상 false. 시스템 부서는 CompanyService 에서만 만든다.
        return new Dept(null, this.deptName, this.deptCode, company, Boolean.FALSE);
    }

    public static DeptDto fromEntity(Dept dept) {
        return new DeptDto(
            dept.getDeptId(),
            dept.getDeptName(),
            dept.getDeptCode(),
            dept.getCompany() != null ? dept.getCompany().getCompanyId() : null,
            dept.isSystemDefault()
        );
    }
}
