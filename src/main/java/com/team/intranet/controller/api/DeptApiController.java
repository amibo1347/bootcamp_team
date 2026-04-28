package com.team.intranet.controller.api;

import com.team.intranet.dto.DeptDto;
import com.team.intranet.service.DeptService;
import com.team.intranet.session.MemberSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/api/admin/dept")
@RequiredArgsConstructor
public class DeptApiController {

    private final DeptService deptService;

    // 부서 생성 처리
    @PostMapping("/create")
    public String createDept(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                             @ModelAttribute DeptDto deptDto,
                             RedirectAttributes redirectAttributes) {
        try {
            deptService.createDept(ms, deptDto);
            redirectAttributes.addFlashAttribute("message", "부서가 성공적으로 생성되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/dept/list";
    }

    // 부서 수정 처리
    @PostMapping("/update/{deptId}")
    public String updateDept(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                             @PathVariable Long deptId,
                             @ModelAttribute DeptDto deptDto,
                             RedirectAttributes redirectAttributes) {
        try {
            deptService.updateDept(ms, deptDto, deptId);
            redirectAttributes.addFlashAttribute("message", "부서 정보가 수정되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/dept/list";
    }

    // 부서 삭제 처리
    @PostMapping("/delete/{deptId}")
    public String deleteDept(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                             @PathVariable Long deptId,
                             RedirectAttributes redirectAttributes) {
        try {
            deptService.deleteDept(ms, deptId);
            redirectAttributes.addFlashAttribute("message", "부서가 삭제되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/dept/list";
    }
}
