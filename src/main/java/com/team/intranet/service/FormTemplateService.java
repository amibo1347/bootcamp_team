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

    /**
     * 관리자용 전체 목록.
     * - 회사 양식 (비활성 포함) 전체
     * - 회사가 fork 하지 않은 시스템 디폴트 (비활성 포함) — fork 진입점 노출용
     * 회사가 fork 한 formCode 는 회사 사본만 보이고 시스템 디폴트는 숨김.
     */
    public List<FormTemplate> listAllForAdmin(MemberSession ms) {
        if (!ms.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        Company company = companyRepository.findById(ms.getCompanyId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        List<FormTemplate> companyOwned = formTemplateRepository.findAllByCompany(company);
        Set<String> ownedCodes = companyOwned.stream()
            .map(FormTemplate::getFormCode)
            .collect(Collectors.toSet());

        List<FormTemplate> defaults = formTemplateRepository.findAllByCompanyIsNull().stream()
            .filter(t -> !ownedCodes.contains(t.getFormCode()))
            .toList();

        return Stream.concat(companyOwned.stream(), defaults.stream()).toList();
    }

    /** 관리자용 단건 조회 — 회사 양식 또는 시스템 디폴트 둘 다 허용. 권한·소속 검증 포함. */
    public FormTemplate getByIdForAdmin(MemberSession ms, Long id) {
        if (!ms.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        FormTemplate t = formTemplateRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORM_TEMPLATE_NOT_FOUND));
        if (t.getCompany() != null
            && !t.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return t;
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
        if (dto.getFormCode() == null || dto.getFormCode().isBlank()
            || dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
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
            .fieldSchema(dto.getFieldSchema())
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

        if (dto.getName() != null && !dto.getName().isBlank()) {
            form.setName(dto.getName());
        }
        form.setContent(dto.getContent());
        form.setActive(dto.isActive());
        // fieldSchema 는 null 일 때 그대로 유지 — A안 단계에선 화면에서 채우지 않음.
        if (dto.getFieldSchema() != null) {
            form.setFieldSchema(dto.getFieldSchema());
        }

        return form;
    }

    /** 회사 양식 삭제. 시스템 디폴트는 삭제 불가 (회사 사본만 본인 회사 권한으로 삭제 가능). */
    @Transactional
    public void deleteTemplate(MemberSession ms, Long id) {
        if (!ms.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        Company company = companyRepository.findById(ms.getCompanyId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        FormTemplate form = formTemplateRepository.findByFormTemplateIdAndCompany(id, company)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORM_TEMPLATE_NOT_FOUND));

        formTemplateRepository.delete(form);
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
            .fieldSchema(systemDefault.getFieldSchema())
            .build();

        return formTemplateRepository.save(fork);
    }
}
