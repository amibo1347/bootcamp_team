package com.team.intranet.controller.api;

import com.team.intranet.dto.DeptDto;
import com.team.intranet.entity.Dept;

import com.team.intranet.service.DeptService;
import com.team.intranet.session.MemberSession;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@RequestMapping("/api/admin/dept")
@RequiredArgsConstructor
public class DeptApiController {

    private final DeptService deptService;

    // 부서 생성 처리
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
    public String createDept(@RequestBody DeptDto dto, Model model, @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        
        // 1. 부서 생성 로직 수행
        deptService.createDept(ms, dto);

        // 2. 갱신된 전체 부서 목록을 다시 조회
        List<Dept> deptList = deptService.findAll(ms.getCompanyId());
        model.addAttribute("departments", deptList);

        return "admin/managingDept :: #deptListContainer";
    }

    // 부서 수정 처리
    @PostMapping("/update/{deptId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
    public String updateDept(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @PathVariable Long deptId,
            @RequestBody DeptDto deptDto, Model model) {

        deptService.editDept(ms, deptId, deptDto);

        List<Dept> deptList = deptService.findAll(ms.getCompanyId());
        model.addAttribute("departments", deptList);
        return "admin/managingDept :: #deptListContainer";
    }

    // 부서 삭제 처리
    @PostMapping("/delete/{deptId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
    public String deleteDept(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @PathVariable Long deptId, Model model) {

        deptService.deleteDept(ms, deptId);

        List<Dept> deptList = deptService.findAll(ms.getCompanyId());
        model.addAttribute("departments", deptList);
        return "admin/managingDept :: #deptListContainer";
    }
}
