package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.dto.AttendanceDto;
import com.team.intranet.dto.AttendancePolicyDto;
import com.team.intranet.service.AttendanceService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final AttendanceService attendanceService;

    @GetMapping({ "/", "/index", "" })
    public String index(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            Model model) {
        // 비로그인 진입 — 출퇴근 카드는 빈 상태로.
        if (ms == null) {
            model.addAttribute("hasTodayCheckIn", Boolean.FALSE);
            model.addAttribute("hasTodayCheckOut", Boolean.FALSE);
            model.addAttribute("todayAttendance", null);
            model.addAttribute("attendancePolicy", null);
            return "index";
        }

        AttendanceDto today = attendanceService.findToday(ms.getMemberId()).orElse(null);
        AttendancePolicyDto policy = attendanceService.getPolicyDto(ms.getCompanyId());

        // 출퇴근 3상태:
        //  - 둘 다 없음 → [출근] 버튼만
        //  - 출근만   → [퇴근] 버튼만
        //  - 둘 다 있음 → [퇴근 취소] 버튼만 (실수 복구용)
        boolean hasClockIn = today != null && today.getClockInTime() != null;
        boolean hasClockOut = today != null && today.getClockOutTime() != null;
        model.addAttribute("hasTodayCheckIn", hasClockIn && !hasClockOut);
        model.addAttribute("hasTodayCheckOut", hasClockOut);
        model.addAttribute("todayAttendance", today);
        model.addAttribute("attendancePolicy", policy);
        return "index";
    }

    @GetMapping("/tables")
    public String tables() {
        return "meterials/basic-tables";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @GetMapping("/calendar")
    public String calendar(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            Model model) {
        // 일정 셀에서 본인 제외 처리를 위해 currentMemberId 를 meta 태그로 노출 (calendar.html)
        model.addAttribute("currentMemberId", ms != null ? ms.getMemberId() : null);
        return "calendar/calendar";
    }

    @GetMapping("/form-elements")
    public String formElements() {
        return "meterials/form-elements";
    }

    @GetMapping("/blank")
    public String blank() {
        return "meterials/blank";
    }

    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }
}
