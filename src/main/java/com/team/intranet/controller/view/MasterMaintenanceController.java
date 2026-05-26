package com.team.intranet.controller.view;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.entity.SystemMaintenance;
import com.team.intranet.service.SystemMaintenanceService;
import com.team.intranet.session.MasterSession;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 의 시스템 점검 모드 관리.
 *  - 접근 제어: masterSecurityFilterChain + MasterTotpGuardFilter (2차 인증 후 진입).
 *  - 수동 토글(즉시 ON/OFF) + 예약(startsAt~endsAt) 둘 다 폼 POST 로 처리.
 *  - 페이지 진입 시 현재 상태(엔티티)와 isCurrentlyOn 을 모델에 같이 담아 화면 분기.
 */
@Controller
@RequestMapping("/master/maintenance")
@RequiredArgsConstructor
public class MasterMaintenanceController {

    /** datetime-local input 의 기본 포맷 — "yyyy-MM-ddTHH:mm". */
    private static final DateTimeFormatter HTML_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final SystemMaintenanceService maintenanceService;

    @GetMapping({"", "/"})
    public String page(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                       Model model) {
        if (master == null) return "redirect:/master/login";
        SystemMaintenance m = maintenanceService.getCurrent();
        model.addAttribute("master", master);
        model.addAttribute("maintenance", m);
        model.addAttribute("active", m.isCurrentlyOn(LocalDateTime.now()));
        // HTML datetime-local 입력 기본값(예약 폼 자동 채움).
        model.addAttribute("startsAtInput", m.getStartsAt() != null ? m.getStartsAt().format(HTML_DT) : "");
        model.addAttribute("endsAtInput",   m.getEndsAt()   != null ? m.getEndsAt().format(HTML_DT)   : "");
        return "master/maintenance";
    }

    /** 수동 토글 — enabled=true/false + reason. */
    @PostMapping("/toggle")
    public String toggle(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                         @RequestParam("enabled") boolean enabled,
                         @RequestParam(name = "reason", required = false) String reason) {
        if (master == null) return "redirect:/master/login";
        maintenanceService.toggle(enabled, reason, master.getMasterAdminId());
        return "redirect:/master/maintenance";
    }

    /** 예약 등록/변경 — datetime-local 두 개. 둘 다 비우면 예약 제거. */
    @PostMapping("/schedule")
    public String schedule(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                           @RequestParam(name = "startsAt", required = false) String startsAtRaw,
                           @RequestParam(name = "endsAt", required = false) String endsAtRaw,
                           @RequestParam(name = "reason", required = false) String reason,
                           Model model) {
        if (master == null) return "redirect:/master/login";

        LocalDateTime startsAt = parseHtmlDateTime(startsAtRaw);
        LocalDateTime endsAt   = parseHtmlDateTime(endsAtRaw);

        try {
            maintenanceService.schedule(startsAt, endsAt, reason, master.getMasterAdminId());
        } catch (IllegalArgumentException e) {
            // 페이지를 다시 보여주되 에러 메시지 동봉.
            model.addAttribute("errorMessage", e.getMessage());
            return page(master, model);
        }
        return "redirect:/master/maintenance";
    }

    /** 예약 해제 — startsAt/endsAt 만 null 로 (수동 enabled 는 그대로). */
    @PostMapping("/clear-schedule")
    public String clearSchedule(@SessionAttribute(name = "masterSession", required = false) MasterSession master) {
        if (master == null) return "redirect:/master/login";
        maintenanceService.clearSchedule(master.getMasterAdminId());
        return "redirect:/master/maintenance";
    }

    private static LocalDateTime parseHtmlDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw, HTML_DT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
