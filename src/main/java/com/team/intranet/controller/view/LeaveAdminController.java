package com.team.intranet.controller.view;

import java.time.Year;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.dto.leave.LeaveRosterView;
import com.team.intranet.service.LeaveBalanceService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 휴가 관리 화면 (회사 관리자 전용).
 *  - 상단: 회사 기본 연차 부여일수 설정(계층1).
 *  - 하단: 직원별 부여/조정/사용/잔여 명부 + 인라인 조정(계층2).
 *  - 권한은 사이드바 노출(VACATION_MANAGE)로 1차 가드, 쓰기 API 에서 역할 가드.
 */
@Controller
@RequestMapping("/admin/leave")
@RequiredArgsConstructor
public class LeaveAdminController {

    private final LeaveBalanceService leaveBalanceService;

    @GetMapping("/list")
    public String leaveList(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                            @RequestParam(value = "year", required = false) Integer year,
                            Model model) {
        if (ms == null || ms.getCompanyId() == null) {
            return "redirect:/company-login";
        }

        int targetYear = (year != null) ? year : Year.now().getValue();
        LeaveRosterView view = leaveBalanceService.getRoster(ms, targetYear);

        model.addAttribute("year", targetYear);
        model.addAttribute("companyDefault", view.getCompanyDefault());
        model.addAttribute("rows", view.getRows());
        model.addAttribute("companyId", ms.getCompanyId());
        // 연도 선택 드롭다운(이전/올해/다음).
        model.addAttribute("yearOptions", List.of(targetYear - 1, targetYear, targetYear + 1));
        return "admin/managingLeave";
    }
}
