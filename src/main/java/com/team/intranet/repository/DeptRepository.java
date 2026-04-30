package com.team.intranet.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.Dept;

public interface DeptRepository extends JpaRepository<Dept, Long>{
    List<Dept> findAllByCompanyCompanyId(Long companyId);
    @Query("SELECT MAX(d.deptCode) FROM Dept d WHERE d.company.companyId = :companyId")
    String findMaxDeptCodeByCompanyId(@Param("companyId") Long companyId);
}
