package com.team.intranet.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.team.intranet.entity.Dept;

public interface DeptRepository extends JpaRepository<Dept, Long>{
    List<Dept> findAllByCompanyCompanyId(Long companyId);
}
