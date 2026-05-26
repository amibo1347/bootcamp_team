package com.team.intranet.entity;

import com.team.intranet.entity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import com.team.intranet.dto.DeptDto;

@Entity
@Table(name="tbl_dept")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Dept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="dept_id")
    private Long deptId;

    @Column(name="dept_name")
    private String deptName;

    @Column(name="dept_code")
    private String deptCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="company_id")
    private Company company;

    /**
     * 시스템 보호 부서 — 회사 생성 시 자동 생성된 "경영지원팀" 이 true.
     *  - 삭제 금지(서비스에서 차단).
     *  - 이름 변경은 자유 (회사가 "총무팀" 등으로 바꿔도 됨).
     *  - 회원 승인 시 부서 필수라 최소 1개 보장이 필요해 시스템이 책임진다.
     */
    @Column(name = "is_system")
    private Boolean isSystem;

    public boolean isSystemDefault() {
        return Boolean.TRUE.equals(isSystem);
    }

    public static Dept createDept(String deptName, String deptCode, Company company){
        Dept dept = new Dept();
        dept.setDeptName(deptName);
        dept.setDeptCode(deptCode);
        dept.setCompany(company);
        dept.setIsSystem(Boolean.FALSE);
        return dept;
    }

    /** 시스템 디폴트 부서 생성 — 회사 생성 시 "경영지원팀" 용. 삭제 차단 대상. */
    public static Dept createSystemDept(String deptName, String deptCode, Company company){
        Dept dept = createDept(deptName, deptCode, company);
        dept.setIsSystem(Boolean.TRUE);
        return dept;
    }

    public void updateFromDto(DeptDto dto) {
        this.deptName = dto.getDeptName();
    }

}
