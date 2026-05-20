package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.service.MasterStatsService;
import com.team.intranet.session.MasterSession;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 회사 사용량 대시보드.
 *  - 접근 제어: masterSecurityFilterChain + MasterTotpGuardFilter.
 */
@Controller
@RequestMapping("/master/usage")
@RequiredArgsConstructor
public class MasterUsageController {

    private final MasterStatsService masterStatsService;

    @GetMapping({"", "/"})
    public String usage(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                        Model model) {
        if (master == null) return "redirect:/master/login";
        model.addAttribute("master", master);
        model.addAttribute("rows", masterStatsService.usageList());
        return "master/usage";
    }
}
