package com.team.intranet.controller.view;

import java.time.LocalDateTime;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.team.intranet.entity.SystemMaintenance;
import com.team.intranet.service.SystemMaintenanceService;

import lombok.RequiredArgsConstructor;

/**
 * 점검 안내 페이지 — 일반 회원(비로그인 포함) 이 점검 중에 보는 화면.
 *  - 점검 모드 여부와 관계없이 GET /maintenance 가 통하도록 SecurityConfig 에서 permitAll.
 *  - 점검이 끝났는데도 사용자가 이 URL 을 열면 "현재 점검 중이 아닙니다" 안내 + 홈 링크.
 */
@Controller
@RequiredArgsConstructor
public class MaintenancePageController {

    private final SystemMaintenanceService maintenanceService;

    @GetMapping("/maintenance")
    public String page(Model model) {
        SystemMaintenance m = maintenanceService.getCurrent();
        boolean active = m.isCurrentlyOn(LocalDateTime.now());
        model.addAttribute("active", active);
        model.addAttribute("reason", m.getReason());
        model.addAttribute("endsAt", m.getEndsAt());
        return "maintenance";
    }
}
