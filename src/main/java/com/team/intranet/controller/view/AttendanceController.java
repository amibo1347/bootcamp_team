package com.team.intranet.controller.view;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.service.AttendanceService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 근태 뷰 컨트롤러.
 *  - /admin/attendance/list : 회사 전체 근태표 (ATTENDANCE_MANAGEMENT)
 *  - /me/attendance         : 본인 월별 근무표 (로그인된 회원 누구나)
 */
@Controller
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/admin/attendance/list")
    @PreAuthorize("isAuthenticated()")
    public String adminAttendanceList(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            Model model) {
        if (ms == null) return "redirect:/company-login";
        // 정책은 페이지 헤더에 표시 (출근/퇴근 시각 안내용)
        model.addAttribute("policy", attendanceService.getPolicyDto(ms.getCompanyId()));
        return "admin/attendanceList";
    }

    @GetMapping("/me/attendance")
    @PreAuthorize("isAuthenticated()")
    public String myAttendance(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            Model model) {
        if (ms == null) return "redirect:/company-login";
        model.addAttribute("policy", attendanceService.getPolicyDto(ms.getCompanyId()));
        return "me/attendance";
    }
}
