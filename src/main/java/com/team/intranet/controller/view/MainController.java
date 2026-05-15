package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.session.MemberSession;

@Controller
public class MainController {

    @GetMapping({ "/", "/index", "" })
    public String index(Model model) {
        // 출퇴근 카드(fragments-attendance) Thymeleaf 표현식용 — 실제 출근 여부는 추후 서비스에서 설정
        model.addAttribute("hasTodayCheckIn", Boolean.FALSE);
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
