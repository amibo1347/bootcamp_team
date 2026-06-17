package com.team.intranet.controller.api;

import java.time.Year;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.team.intranet.config.AuthenticatedMember;
import com.team.intranet.dto.leave.LeaveRosterView;
import com.team.intranet.service.LeaveBalanceService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 휴가 관리 쓰기 API.
 *  - POST /api/admin/leave/default        : 회사 기본 연차 부여일수 변경(계층1)
 *  - POST /api/admin/leave/member/{id}    : 직원 1명 부여 연차 설정(계층2)
 *  - POST /api/admin/leave/bulk           : 선택한 여러 직원에 부여 연차 일괄 설정
 *  모두 갱신된 명부 fragment(#leaveListContainer)를 돌려줘 화면을 부분 교체한다.
 */
@Controller
@RequestMapping("/api/admin/leave")
@RequiredArgsConstructor
public class LeaveAdminApiController {

    private final LeaveBalanceService leaveBalanceService;

    @PostMapping("/default")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
    public String updateDefault(@AuthenticatedMember MemberSession ms,
                                @RequestBody DefaultRequest body, Model model) {
        double days = (body.days() != null) ? body.days() : -1.0; // null → 서비스에서 검증 실패 처리
        leaveBalanceService.updateCompanyDefault(ms, days);
        reloadRoster(ms, body.year(), model);
        return "admin/managingLeave :: #leaveListContainer";
    }

    @PostMapping("/member/{memberId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
    public String upsertMember(@AuthenticatedMember MemberSession ms,
                               @PathVariable Long memberId,
                               @RequestBody MemberRequest body, Model model) {
        int year = (body.year() != null) ? body.year() : Year.now().getValue();
        double granted = (body.granted() != null) ? body.granted() : -1.0; // null → 검증 실패
        leaveBalanceService.upsertMemberBalance(ms, memberId, year, granted, body.note());
        reloadRoster(ms, body.year(), model);
        return "admin/managingLeave :: #leaveListContainer";
    }

    /** 선택한 여러 직원에 같은 부여 연차 일괄 적용. */
    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
    public String bulkSetGranted(@AuthenticatedMember MemberSession ms,
                                 @RequestBody BulkRequest body, Model model) {
        int year = (body.year() != null) ? body.year() : Year.now().getValue();
        double granted = (body.granted() != null) ? body.granted() : -1.0; // null → 검증 실패
        leaveBalanceService.bulkSetGranted(ms, body.memberIds(), year, granted);
        reloadRoster(ms, body.year(), model);
        return "admin/managingLeave :: #leaveListContainer";
    }

    private void reloadRoster(MemberSession ms, Integer year, Model model) {
        int targetYear = (year != null) ? year : Year.now().getValue();
        LeaveRosterView view = leaveBalanceService.getRoster(ms, targetYear);
        model.addAttribute("year", targetYear);
        model.addAttribute("companyDefault", view.getCompanyDefault());
        model.addAttribute("rows", view.getRows());
        model.addAttribute("companyId", ms.getCompanyId());
    }

    /** 회사 기본 부여일수 변경 요청. days=새 기본일수, year=현재 보고 있는 연도(명부 재조회용). */
    public record DefaultRequest(Double days, Integer year) {}

    /** 직원별 연차 설정 요청. granted=부여, note=메모, year=대상 연도. */
    public record MemberRequest(Double granted, String note, Integer year) {}

    /** 일괄 부여 요청. memberIds=대상 직원, granted=부여 연차, year=대상 연도. */
    public record BulkRequest(List<Long> memberIds, Double granted, Integer year) {}
}
