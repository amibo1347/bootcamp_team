package com.team.intranet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.AttendancePolicy;

public interface AttendancePolicyRepository extends JpaRepository<AttendancePolicy, Long> {

    Optional<AttendancePolicy> findByCompany_CompanyId(Long companyId);
}
