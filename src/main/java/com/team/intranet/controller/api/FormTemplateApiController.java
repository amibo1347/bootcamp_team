package com.team.intranet.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.dto.FormTemplateDto;
import com.team.intranet.dto.approval.FormTemplateResponse;
import com.team.intranet.service.FormTemplateService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 결재 양식 관리 API (admin 용).
 * 베이스: /api/approval/admin/form-templates
 *
 * 프로젝트 규칙상 조회는 GET, 변경은 POST 만 사용.
 */
@RestController
@RequestMapping("/api/approval/admin/form-templates")
@RequiredArgsConstructor
public class FormTemplateApiController {

    private final FormTemplateService formTemplateService;

    /** 회사 양식(비활성 포함) + (아직 fork 안 한) 시스템 디폴트 전체 목록. */
    @GetMapping
    public List<FormTemplateResponse> listAll(
            @SessionAttribute("memberSession") MemberSession ms) {
        return formTemplateService.listAllForAdmin(ms).stream()
            .map(FormTemplateResponse::from)
            .toList();
    }

    /** 단건 상세 (수정 모달 진입). */
    @GetMapping("/{id}")
    public FormTemplateResponse getOne(
            @SessionAttribute("memberSession") MemberSession ms,
            @PathVariable("id") Long id) {
        return FormTemplateResponse.from(formTemplateService.getByIdForAdmin(ms, id));
    }

    /** 시스템 디폴트를 회사 스코프로 복사 — 커스터마이즈 진입점. */
    @PostMapping("/fork")
    public FormTemplateResponse fork(
            @SessionAttribute("memberSession") MemberSession ms,
            @RequestBody Map<String, String> body) {
        String formCode = body == null ? null : body.get("formCode");
        return FormTemplateResponse.from(formTemplateService.forkTemplate(ms, formCode));
    }

    /** 새 양식 생성 (회사 스코프). formCode 는 회사 안에서 UNIQUE 여야 한다. */
    @PostMapping
    public FormTemplateResponse create(
            @SessionAttribute("memberSession") MemberSession ms,
            @RequestBody FormTemplateDto dto) {
        return FormTemplateResponse.from(formTemplateService.createTemplate(ms, dto));
    }

    /** 회사 양식 수정. 시스템 디폴트는 본 엔드포인트로 수정 불가 (fork 후 회사 사본을 수정해야 함). */
    @PostMapping("/{id}")
    public FormTemplateResponse update(
            @SessionAttribute("memberSession") MemberSession ms,
            @PathVariable("id") Long id,
            @RequestBody FormTemplateDto dto) {
        return FormTemplateResponse.from(formTemplateService.updateTemplate(ms, id, dto));
    }

    /** 회사 양식 삭제. 삭제 후 동일 formCode 의 시스템 디폴트가 있으면 자동으로 다시 노출된다. */
    @PostMapping("/{id}/delete")
    public Map<String, Object> delete(
            @SessionAttribute("memberSession") MemberSession ms,
            @PathVariable("id") Long id) {
        formTemplateService.deleteTemplate(ms, id);
        return Map.of("success", true, "id", id);
    }
}
