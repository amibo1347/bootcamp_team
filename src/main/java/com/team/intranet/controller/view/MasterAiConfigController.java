package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.session.MasterSession;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 의 AI 설정 페이지.
 *  - 접근 제어: masterSecurityFilterChain + MasterTotpGuardFilter (2차 인증까지 통과).
 *  - REST API 는 AdminAiConfigController(@RequestMapping("/api/master/ai")) 에 이미 구현되어 있고,
 *    이 컨트롤러는 화면(템플릿) 만 담당. 폼 동작은 페이지의 fetch JS 가 API 를 호출.
 */
@Controller
@RequestMapping("/master/ai-config")
@RequiredArgsConstructor
public class MasterAiConfigController {

    @GetMapping({"", "/"})
    public String aiConfigPage(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                               Model model) {
        if (master == null) return "redirect:/master/login";
        model.addAttribute("master", master);
        return "master/ai-config";
    }
}
