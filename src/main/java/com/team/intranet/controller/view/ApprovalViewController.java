package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ApprovalViewController {

    // 전자결재 메인 페이지 (탭으로 wizard/my/pending/completed 전환)
    // session.memberSession 은 Spring 이 자동 노출 → Thymeleaf 에서 바로 접근
    @GetMapping("/approval")
    public String approval() {
        return "approval/approval";
    }
}
