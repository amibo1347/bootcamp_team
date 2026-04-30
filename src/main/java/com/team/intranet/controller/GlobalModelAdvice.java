package com.team.intranet.controller;

import com.team.intranet.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.SessionAttribute;
import com.team.intranet.session.MemberSession;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {
    private final MemberService memberService;

    @ModelAttribute
    public void addLogoPath(Model model, @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms != null && ms.getCompanyId() != null) {
            String logoPath = memberService.getLogoPath(ms.getCompanyId());
            model.addAttribute("logoPath", logoPath);
        }
        else {
            model.addAttribute("logoPath", null);
        }
    }
}
