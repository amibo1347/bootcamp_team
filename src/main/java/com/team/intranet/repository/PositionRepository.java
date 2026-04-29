package com.team.intranet.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.Position;

public interface  PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findAllByCompanyCompanyId(Long companyId);
    List<Position> findByCompany_CompanyIdOrderByPositionLevelAsc(Long companyId);
    List<Position> findByCompany_CompanyIdOrderByPositionLevelDesc(Long companyId);
}
