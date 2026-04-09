package com.team.intranet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.team.intranet.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long>{
    Optional<Company> findByCompanyCode(String companyCode);
}
