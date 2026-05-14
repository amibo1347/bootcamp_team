package com.team.intranet.service;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.dto.FormTemplateDto;
import com.team.intranet.repository.FormTemplateRepository;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.session.MemberSession;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.FormTemplate;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.exception.BusinessException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FormTemplateService {

    private final FormTemplateRepository formTemplateRepository;
    private final CompanyRepository companyRepository;

    // 회사 스코프 양식 + (회사가 오버라이드 안 한) 시스템 디폴트
    public List<FormTemplateDto> getActiveTemplates(MemberSession ms){
        Company company = companyRepository.findById(ms.getCompanyId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        List<FormTemplate> companyOwned = formTemplateRepository.findAllByCompanyAndIsActiveTrue(company);
        Set<String> ownedCodes = companyOwned.stream()
            .map(FormTemplate::getFormCode)
            .collect(Collectors.toSet());

        List<FormTemplate> defaults = formTemplateRepository.findAllByCompanyIsNullAndIsActiveTrue().stream()
            .filter(t -> !ownedCodes.contains(t.getFormCode()))
            .toList();

        return Stream.concat(companyOwned.stream(), defaults.stream())
            .map(FormTemplateDto::from)
            .toList();
    }

    // 회사 양식이 있으면 그것, 없으면 시스템 디폴트
    public FormTemplate getByFormCode(MemberSession ms, String formCode){
        Company company = companyRepository.findById(ms.getCompanyId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        FormTemplate formTemplate = formTemplateRepository.findByCompanyAndFormCode(company, formCode)
            .or(() -> formTemplateRepository.findByCompanyIsNullAndFormCode(formCode))
            .orElseThrow(() -> new BusinessException(ErrorCode.FORM_TEMPLATE_NOT_FOUND));

        if (!formTemplate.isActive()) {
            throw new BusinessException(ErrorCode.FORM_TEMPLATE_INACTIVE);
        }

        return formTemplate;
    }

    @Transactional
    public FormTemplate createTemplate(MemberSession ms, FormTemplateDto dto){
        if (!ms.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }

        Company company = companyRepository.findById(ms.getCompanyId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        if(formTemplateRepository.existsByCompanyAndFormCode(company, dto.getFormCode())){
            throw new BusinessException(ErrorCode.DUPLICATE_FORM_CODE);
        }

        FormTemplate form = FormTemplate.builder()
            .company(company)
            .formCode(dto.getFormCode())
            .name(dto.getName())
            .content(dto.getContent())
            .isActive(true)
            .build();

        return formTemplateRepository.save(form);
    }

    @Transactional
    public FormTemplate updateTemplate(MemberSession ms, Long id, FormTemplateDto dto){
        if (!ms.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }

        Company company = companyRepository.findById(ms.getCompanyId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        FormTemplate form = formTemplateRepository.findByFormTemplateIdAndCompany(id, company)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORM_TEMPLATE_NOT_FOUND));

        form.setName(dto.getName());
        form.setContent(dto.getContent());
        form.setActive(dto.isActive());

        return form;
    }

    // 시스템 디폴트를 회사 스코프로 복사 (커스터마이즈 진입점)
    @Transactional
    public FormTemplate forkTemplate(MemberSession ms, String formCode){
        if (!ms.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }

        Company company = companyRepository.findById(ms.getCompanyId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        if (formTemplateRepository.existsByCompanyAndFormCode(company, formCode)) {
            throw new BusinessException(ErrorCode.DUPLICATE_FORM_CODE);
        }

        FormTemplate systemDefault = formTemplateRepository.findByCompanyIsNullAndFormCode(formCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORM_TEMPLATE_NOT_FOUND));

        FormTemplate fork = FormTemplate.builder()
            .company(company)
            .formCode(systemDefault.getFormCode())
            .name(systemDefault.getName())
            .content(systemDefault.getContent())
            .isActive(true)
            .build();

        return formTemplateRepository.save(fork);
    }
}
