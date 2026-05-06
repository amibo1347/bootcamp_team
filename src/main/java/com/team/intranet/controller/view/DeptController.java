package com.team.intranet.controller.view;

import com.team.intranet.entity.Dept;
import com.team.intranet.service.DeptService;
import com.team.intranet.session.MemberSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.List;

@Controller
@RequestMapping("/admin/dept")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    // 부서 관리 메인 페이지 (목록 조회)
    @GetMapping("/list")
    public String deptList(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                           Model model) {
        if (ms == null || ms.getCompanyId() == null) {
            return "redirect:/member/login";
        }

        List<Dept> depts = deptService.findAll(ms.getCompanyId());
        model.addAttribute("departments", depts);
        model.addAttribute("companyId", ms.getCompanyId());
        return "admin/managingDept";
    }
}