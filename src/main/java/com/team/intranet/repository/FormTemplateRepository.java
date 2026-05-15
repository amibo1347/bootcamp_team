package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.team.intranet.entity.FormTemplate;
import com.team.intranet.entity.Company;
import java.util.List;
import java.util.Optional;
public interface FormTemplateRepository extends JpaRepository<FormTemplate, Long>{

    // 회사 스코프 양식
    Optional<FormTemplate> findByCompanyAndFormCode(Company company, String formCode);
    Optional<FormTemplate> findByFormTemplateIdAndCompany(Long formTemplateId, Company company);
    List<FormTemplate> findAllByCompanyAndIsActiveTrue(Company company);
    List<FormTemplate> findAllByCompany(Company company); // 관리자 목록 — 비활성 포함
    boolean existsByCompanyAndFormCode(Company company, String formCode);

    // 시스템 디폴트 양식 (company_id IS NULL)
    Optional<FormTemplate> findByCompanyIsNullAndFormCode(String formCode);
    List<FormTemplate> findAllByCompanyIsNullAndIsActiveTrue();
    List<FormTemplate> findAllByCompanyIsNull(); // 관리자 목록 — 비활성 포함
}
