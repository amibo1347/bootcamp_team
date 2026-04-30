package com.team.intranet.controller.api;

import com.team.intranet.dto.DeptDto;
import com.team.intranet.entity.Dept;
import com.team.intranet.enums.member.Role;
import com.team.intranet.service.DeptService;
import com.team.intranet.session.MemberSession;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.ui.Model;

@Controller
@RequestMapping("/api/admin/dept")
@RequiredArgsConstructor
public class DeptApiController {

    private final DeptService deptService;

    // 부서 생성 처리
    @PostMapping("/create")
    public String createDept(@RequestBody DeptDto dto, Model model, @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        
        if(ms == null || ms.getCompanyId() == null) {
            return "redirect:/member/login";
        }
        if(ms.getRole() == Role.USER) {
            return "redirect:/member/login";
        }
        // 1. 부서 생성 로직 수행
        deptService.createDept(ms, dto);

        // 2. 갱신된 전체 부서 목록을 다시 조회
        List<Dept> deptList = deptService.findAll(ms.getCompanyId());
        model.addAttribute("departments", deptList);

        return "admin/managingDept :: #deptListContainer";
    }

    // 부서 수정 처리
    @PostMapping("/update/{deptId}")
    public String updateDept(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @PathVariable Long deptId,
            @RequestBody DeptDto deptDto) {
        try {
            deptService.updateDept(ms, deptDto, deptId);
        } catch (Exception e) {
            return "redirect:/member/login";
        }
        return "admin/managingDept :: #deptListContainer";
    }

    // 부서 삭제 처리
    @PostMapping("/delete/{deptId}")
    public String deleteDept(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @PathVariable Long deptId) {
        try {
            deptService.deleteDept(ms, deptId);
            return "admin/managingDept :: #deptListContainer";
        } catch (Exception e) {
            return "redirect:/member/login";
        }
    }
}
