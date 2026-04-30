package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.team.intranet.session.MemberSession;

import jakarta.servlet.http.HttpServletRequest;

import com.team.intranet.service.MemberService;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.member.Status;

import com.team.intranet.service.DeptService;
import com.team.intranet.dto.MemberDto;
import com.team.intranet.entity.Dept;

import com.team.intranet.service.PositionService;
import com.team.intranet.entity.Position;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    /////////////////////////////////////
    /// 의존성 주입
    private final MemberService memberService;
    private final DeptService deptService;
    private final PositionService positionService;

    /////////////////////////////////////
    /// 컨트롤러

    @GetMapping("/waitingList")
    public String joinList(Model model,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {

        if (ms == null || ms.getCompanyId() == null) {
            return "redirect:/member/login";
        }
        Long companyId = ms.getCompanyId();

        List<Member> waitingMembers = memberService.findWaitMembers(companyId);

        List<Dept> depts = deptService.findAll(companyId);
        List<Position> positions = positionService.findAll(companyId);

        model.addAttribute("members", waitingMembers); // 승인 대기중인 회원
        model.addAttribute("depts", depts); // 부서 목록
        model.addAttribute("positions", positions); // 직급 목록

        // 새로 추가했음 확인!!!!!!
        model.addAttribute("count", waitingMembers.size()); // 승인 대기중인 회원 수
        return "subAdmin/waitingList";
    }

    @GetMapping("/memberList")
    public String memberList(Model model,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "positionId", required = false) Long positionId,
            @RequestParam(value = "status", required = false) Status status,
            @RequestParam(value = "sort", defaultValue = "asc") String sort,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            HttpServletRequest request) {

        if (ms == null || ms.getCompanyId() == null) {
            return "redirect:/member/login";
        }
        Long companyId = ms.getCompanyId();

        List<Member> filteredMembers = memberService.findFilteredMembers(companyId, deptId, status, positionId, sort);

        List<Dept> depts = deptService.findAll(companyId);
        List<Position> positions = positionService.findAll(companyId);

        model.addAttribute("members", filteredMembers); // 회원 목록
        model.addAttribute("depts", depts); // 부서 목록
        model.addAttribute("positions", positions); // 직급 목록

        model.addAttribute("selectedDeptId", deptId); // 선택된 부서 ID
        model.addAttribute("selectedPositionId", positionId); // 선택된 직급 ID
        model.addAttribute("selectedStatus", status); // 선택된 상태
        model.addAttribute("currentSort", sort); // 현재 정렬 방식

        String requestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equals(requestedWith)) {
            // 해당 html 파일 내의 fragment 경로만 리턴
            return "subAdmin/memberList :: memberListFragment";
        }

        return "subAdmin/memberList";
    }
}
