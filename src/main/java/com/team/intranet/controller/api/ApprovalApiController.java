package com.team.intranet.controller.api;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.dto.approval.ApprovalDetailResponse;
import com.team.intranet.dto.approval.ApprovalListResponse;
import com.team.intranet.dto.approval.ApprovalPageResponse;
import com.team.intranet.dto.approval.ApprovalProcessRequest;
import com.team.intranet.dto.approval.ApprovalProcessResponse;
import com.team.intranet.dto.approval.ApprovalSubmitRequest;
import com.team.intranet.dto.approval.ApprovalSubmitResponse;
import com.team.intranet.dto.approval.ApproverCandidate;
import com.team.intranet.dto.approval.FormTemplateResponse;
import com.team.intranet.dto.approval.VacationTypeOption;
import com.team.intranet.enums.VacationType;
import com.team.intranet.service.ApprovalService;
import com.team.intranet.service.FormTemplateService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 전자결재 API (프론트 approval-client.js 와 1:1).
 * 베이스: /api/approval
 */
@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
public class ApprovalApiController {

    private final FormTemplateService formTemplateService;
    private final ApprovalService approvalService;

    // 회사 스코프 + 시스템 디폴트 활성 양식
    @GetMapping("/form-templates")
    public List<FormTemplateResponse> getFormTemplates(
            @SessionAttribute("memberSession") MemberSession ms) {
        return formTemplateService.getActiveTemplates(ms).stream()
            .map(FormTemplateResponse::fromDto)
            .toList();
    }

    // 결재자 후보 (신청자 role 기준 필터링: USER→SUB_ADMIN만, SUB_ADMIN+→SUB_ADMIN/ADMIN/MASTER)
    @GetMapping("/approver-candidates")
    public List<ApproverCandidate> getApproverCandidates(
            @SessionAttribute("memberSession") MemberSession ms) {
        return approvalService.listApproverCandidates(ms);
    }

    // 휴가 유형 enum 옵션
    @GetMapping("/vacation-types")
    public List<VacationTypeOption> getVacationTypes() {
        return Arrays.stream(VacationType.values())
            .map(VacationTypeOption::from)
            .toList();
    }

    // 결재 제출
    @PostMapping("/submit")
    public ApprovalSubmitResponse submit(
            @SessionAttribute("memberSession") MemberSession ms,
            @RequestBody ApprovalSubmitRequest req) {
        return approvalService.submit(ms, req);
    }

    // 내 결재함 (drafter == 본인). status: ALL|PENDING|APPROVED|REJECTED
    @GetMapping("/my")
    public ApprovalPageResponse listMy(
            @SessionAttribute("memberSession") MemberSession ms,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page) {
        return approvalService.listMyApprovals(ms, status, page);
    }

    // 관리자 대기함 (approver == 본인, PENDING)
    @GetMapping("/admin/pending")
    public ApprovalListResponse listPending(
            @SessionAttribute("memberSession") MemberSession ms) {
        return approvalService.listPendingForAdmin(ms);
    }

    // 관리자 완료함 (approver == 본인, APPROVED/REJECTED)
    @GetMapping("/admin/completed")
    public ApprovalListResponse listCompleted(
            @SessionAttribute("memberSession") MemberSession ms) {
        return approvalService.listCompletedForAdmin(ms);
    }

    // 결재 단건 상세 (헤더 + 결재선 + 양식별 본문). 권한: 기안자 본인 또는 결재선에 포함된 결재자.
    @GetMapping("/{approvalId}")
    public ApprovalDetailResponse getDetail(
            @SessionAttribute("memberSession") MemberSession ms,
            @PathVariable("approvalId") Long approvalId) {
        return approvalService.getApprovalDetail(ms, approvalId);
    }

    // 결재 처리 (APPROVE | REJECT | HOLD).
    @PostMapping("/process")
    public ApprovalProcessResponse process(
            @SessionAttribute("memberSession") MemberSession ms,
            @RequestBody ApprovalProcessRequest req) {
        return approvalService.process(ms, req);
    }
}
