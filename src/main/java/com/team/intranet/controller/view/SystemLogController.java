package com.team.intranet.controller.view;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.dto.SystemLogDto;
import com.team.intranet.enums.SystemLogPeriodType;
import com.team.intranet.service.SystemLogService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * ADMIN 전용 — 회사 시스템 로그 페이지.
 *  - URL 가드: /admin/** 은 SecurityConfig 에서 hasRole("ADMIN") 으로 잡힘. 컨트롤러 @PreAuthorize 로 이중 안전망.
 *  - 회사 격리: ms.getCompanyId() 만 조회.
 *  - 모드: mode=raw(보관 기간 안) / mode=daily / mode=monthly / mode=quarterly (AI 요약)
 */
@Controller
@RequestMapping("/admin/system-log")
@RequiredArgsConstructor
public class SystemLogController {

    private static final int PAGE_SIZE = 20;

    private final SystemLogService systemLogService;

    /** 페이지 진입 — 빈 셸만 렌더. 목록은 JS 가 API 로 페이징 호출. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String page(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                       Model model) {
        systemLogService.assertAdmin(ms);
        model.addAttribute("memberSession", ms);
        return "admin/system-log";
    }

    /**
     * 시스템 로그 목록 API.
     *  - mode=raw (기본): 최신 보관 기간 내 raw row
     *  - mode=daily / monthly / quarterly: AI 요약 row
     */
    @GetMapping("/api")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public Page<SystemLogDto> list(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                                   @RequestParam(defaultValue = "raw") String mode,
                                   @RequestParam(defaultValue = "0") int page) {
        systemLogService.assertAdmin(ms);
        Long companyId = ms.getCompanyId();
        int safePage = Math.max(0, page);
        var pageable = PageRequest.of(safePage, PAGE_SIZE);

        return switch (mode.toLowerCase()) {
            case "daily"     -> systemLogService.findSummaries(companyId, SystemLogPeriodType.DAY, pageable);
            case "monthly"   -> systemLogService.findSummaries(companyId, SystemLogPeriodType.MONTH, pageable);
            case "quarterly" -> systemLogService.findSummaries(companyId, SystemLogPeriodType.QUARTER, pageable);
            default          -> systemLogService.findRawLogs(companyId, pageable);
        };
    }
}
