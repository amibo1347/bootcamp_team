package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import lombok.RequiredArgsConstructor;
import java.util.List;

import com.team.intranet.session.MemberSession;
import com.team.intranet.service.MemberService;
import com.team.intranet.entity.Member;

import com.team.intranet.service.DeptService;
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

        // List<Dept> depts = deptService.findAll(companyId);
        List<Position> positions = positionService.findAll(companyId);

        model.addAttribute("members", waitingMembers); // 승인 대기중인 회원
        // model.addAttribute("depts", depts); // 부서 목록
        model.addAttribute("positions", positions); // 직급 목록

        // 새로 추가했음 확인!!!!!!
        model.addAttribute("count", waitingMembers.size()); // 승인 대기중인 회원 수
        return "subAdmin/waitingList";
    }

    @GetMapping("/memberList")
    public String memberList(Model model,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {

        if (ms == null || ms.getCompanyId() == null) {
            return "redirect:/member/login";
        }
        Long companyId = ms.getCompanyId();

        List<Member> joinMembers = memberService.findJoinMembers(companyId);

        List<Dept> depts = deptService.findAll(companyId);
        List<Position> positions = positionService.findAll(companyId);

        model.addAttribute("members", joinMembers); // 승인 대기중인 회원
        model.addAttribute("depts", depts); // 부서 목록
        model.addAttribute("positions", positions); // 직급 목록

        return "subAdmin/memberList";
    }

}
