package com.team.intranet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.team.intranet.entity.Company;
import com.team.intranet.enums.IsActive;

public interface CompanyRepository extends JpaRepository<Company, Long>{
    Optional<Company> findByCompanyCodeIgnoreCase(String companyCode);
    boolean existsByCompanyCode(String companyCode);

    /** 회사명 부분 일치 검색 (MASTER 회사 목록). */
    List<Company> findByCompanyNameContainingIgnoreCaseOrderByCompanyIdAsc(String keyword);

    /** 로고 BLOB 을 로딩하지 않고 로고 보유 여부만 확인. */
    boolean existsByCompanyIdAndLogoIsNotNull(Long companyId);

    /** 회사가 특정 활성 상태인지 확인 (비활성 회사 회원 자동 로그아웃 판정용). */
    boolean existsByCompanyIdAndIsActive(Long companyId, IsActive isActive);
}
