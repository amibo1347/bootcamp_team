package com.team.intranet.service;

import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.intranet.dto.FormTemplateDto;
import com.team.intranet.enums.VacationType;
import com.team.intranet.repository.FormTemplateRepository;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.session.MemberSession;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.FormTemplate;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.exception.BusinessException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FormTemplateService {

    private final FormTemplateRepository formTemplateRepository;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;

    // 신청 wizard 가 사용 — 그 회사 사용자에게 노출할 양식 목록.
    //
    // 로직:
    //  1) 회사 사본이 form_code X 로 존재하면 (active 여부 무관) X 는 회사가 명시적으로 관리 중인 코드.
    //     → 같은 form_code 의 시스템 디폴트는 항상 숨김.
    //  2) 회사 사본 중 active=true 인 것만 결과에 포함.
    //  3) 회사가 사본을 만들지 않은 시스템 디폴트는 active=true 인 것만 fallback 으로 포함.
    //
    // 결과: 회사가 회사 사본 X 를 비활성화하면 신청 wizard 에서 해당 양식 자체가 사라짐
    // (시스템 디폴트로도 fallback 되지 않음 — "양식 막기" UX).
    public List<FormTemplateDto> getActiveTemplates(MemberSession ms){
        Company company = companyRepository.findById(ms.getCompanyId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        List<FormTemplate> allCompanyOwned = formTemplateRepository.findAllByCompany(company);
        Set<String> ownedCodes = allCompanyOwned.stream()
            .map(FormTemplate::getFormCode)
            .collect(Collectors.toSet());

        List<FormTemplate> activeCompanyOwned = allCompanyOwned.stream()
            .filter(FormTemplate::isActive)
            .toList();

        List<FormTemplate> defaults = formTemplateRepository.findAllByCompanyIsNullAndIsActiveTrue().stream()
            .filter(t -> !ownedCodes.contains(t.getFormCode()))
            .toList();

        return Stream.concat(activeCompanyOwned.stream(), defaults.stream())
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
        if (!ms.isAdminOrMaster()) {
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
        if (!ms.isAdminOrMaster()) {
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
        if (!ms.isAdminOrMaster()) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        if (dto.getFormCode() == null || dto.getFormCode().isBlank()
            || dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        Company company = companyRepository.findById(ms.getCompanyId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        String code = dto.getFormCode().trim().toUpperCase();

        // 회사 사본 중복
        if (formTemplateRepository.existsByCompanyAndFormCode(company, code)) {
            throw new BusinessException(ErrorCode.DUPLICATE_FORM_CODE);
        }
        // 시스템 디폴트와 같은 코드는 신규 생성 차단 — fork 해야 의도에 맞고, DB UK 가 form_code 단독으로
        // 걸려있을 가능성도 사전 차단. (UK 가 (company_id, form_code) 복합이어도 UX 상 fork 가 정답.)
        if (formTemplateRepository.findByCompanyIsNullAndFormCode(code).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_FORM_CODE);
        }

        FormTemplate form = FormTemplate.builder()
            .company(company)
            .formCode(code)
            .name(dto.getName())
            .content(dto.getContent())
            .isActive(true)
            .fieldSchema(dto.getFieldSchema())
            .build();

        return formTemplateRepository.save(form);
    }

    @Transactional
    public FormTemplate updateTemplate(MemberSession ms, Long id, FormTemplateDto dto){
        if (!ms.isAdminOrMaster()) {
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
        if (!ms.isAdminOrMaster()) {
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
        if (!ms.isAdminOrMaster()) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }

        Company company = companyRepository.findById(ms.getCompanyId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        if (formTemplateRepository.existsByCompanyAndFormCode(company, formCode)) {
            throw new BusinessException(ErrorCode.DUPLICATE_FORM_CODE);
        }

        FormTemplate systemDefault = formTemplateRepository.findByCompanyIsNullAndFormCode(formCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORM_TEMPLATE_NOT_FOUND));

        // fork 직후 회사 사본은 동적 본문 경로로 전환됨. 시스템 디폴트(VACATION/GENERIC/EXPENSE)는
        // fixed-schema 본문이라 fieldSchema 가 NULL — 사용자가 admin 모달을 열었을 때 빈 양식이 되지 않도록
        // fixed 본문 모양에 대응하는 schema JSON 을 자동으로 채워준다. 거기서 추가/수정 가능.
        String inheritedSchema = systemDefault.getFieldSchema();
        if (inheritedSchema == null || inheritedSchema.isBlank()) {
            inheritedSchema = defaultSchemaForFormCode(systemDefault.getFormCode());
        }

        FormTemplate fork = FormTemplate.builder()
            .company(company)
            .formCode(systemDefault.getFormCode())
            .name(systemDefault.getName())
            .content(systemDefault.getContent())
            .isActive(true)
            .fieldSchema(inheritedSchema)
            .build();

        return formTemplateRepository.save(fork);
    }

    /** 시스템 디폴트 fixed 본문 모양에 대응하는 동적 schema JSON. 매칭 안 되면 null. */
    private String defaultSchemaForFormCode(String formCode) {
        if (formCode == null) return null;
        return switch (formCode.toUpperCase()) {
            case "VACATION" -> serializeSchemaSafely(buildVacationDefaultSchema(), "VACATION");
            case "GENERIC"  -> serializeSchemaSafely(buildGenericDefaultSchema(), "GENERIC");
            case "EXPENSE"  -> serializeSchemaSafely(buildExpenseDefaultSchema(), "EXPENSE");
            default -> null;
        };
    }

    private List<Map<String, Object>> buildVacationDefaultSchema() {
        List<String> options = Arrays.stream(VacationType.values())
            .map(VacationType::getDescription)
            .toList();
        // 원본 휴가 양식 layout 재현: 휴가종류·일수 가로(6+6) / 시작일·종료일 가로(6+6) / 사유 한 줄(12).
        // 일수는 시작일·종료일에서 자동 계산되는 computed 필드.
        return List.of(
            Map.of("key", "vacation_type", "label", "휴가 종류", "type", "select",
                   "required", true, "options", options,
                   "row", 1, "col", 1, "span", 6),
            Map.of("key", "days", "label", "일수", "type", "computed", "required", false,
                   "computed", Map.of("fn", "diff_days_plus_1",
                                      "args", Map.of("start", "start_date", "end", "end_date")),
                   "row", 1, "col", 7, "span", 6),
            Map.of("key", "start_date", "label", "시작일", "type", "date", "required", true,
                   "row", 2, "col", 1, "span", 6),
            Map.of("key", "end_date", "label", "종료일", "type", "date", "required", true,
                   "row", 2, "col", 7, "span", 6),
            Map.of("key", "reason", "label", "사유", "type", "textarea", "required", false,
                   "rows", 4, "row", 3, "col", 1, "span", 12)
        );
    }

    private List<Map<String, Object>> buildGenericDefaultSchema() {
        return List.of(
            Map.of("key", "content", "label", "본문", "type", "textarea", "required", true,
                   "rows", 6, "row", 1, "col", 1, "span", 12)
        );
    }

    private List<Map<String, Object>> buildExpenseDefaultSchema() {
        return List.of(
            Map.of("key", "amount", "label", "금액 (원)", "type", "number", "required", true,
                   "row", 1, "col", 1, "span", 6),
            Map.of("key", "category", "label", "분류", "type", "text", "required", true,
                   "row", 1, "col", 7, "span", 6),
            Map.of("key", "spent_at", "label", "지출일", "type", "date", "required", true,
                   "row", 2, "col", 1, "span", 6),
            Map.of("key", "description", "label", "상세 내역", "type", "textarea", "required", false,
                   "rows", 4, "row", 3, "col", 1, "span", 12)
        );
    }

    private String serializeSchemaSafely(List<Map<String, Object>> schema, String code) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            log.warn("{} 기본 schema 직렬화 실패: {}", code, e.getMessage());
            return null;
        }
    }
}
