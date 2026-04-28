package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

import jakarta.servlet.http.HttpSession;
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
        return "admin/waitingList";
    }

    // 가입 승인용 포스트매핑
    @PostMapping("/accept/{id}")
    public String approveMember(
            @PathVariable("id") Long memberId,
            @RequestParam Long deptId,
            @RequestParam Long positionId,
            @SessionAttribute("memberSession") MemberSession ms) { // 세션 바로 가져오기

        // 서비스의 파라미터 타입에 맞춰서 ms.getLoginId() 혹은 ms.getMemberId() 전달
        memberService.acceptMember(memberId, ms.getMemberId(), deptId, positionId);
        return "redirect:/admin/waitingList";
    }

    // 가입 반려 (거절)
    @PostMapping("/reject/{id}")
    public String rejectMember(
            @PathVariable("id") Long memberId,
            @SessionAttribute("memberSession") MemberSession ms) {

        // 반려 시에도 관리자 권한 확인을 위해 ms.getMemberId() 전달 추천
        memberService.rejectMember(memberId, ms.getMemberId());
        return "redirect:/admin/waitingList";
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

        return "admin/memberList";
    }

    // 회원 정보 수정 (부서, 직급 변경)
    @PostMapping("/update/{id}")
    public String updateMember(
            @PathVariable("id") Long memberId,
            @RequestParam Long deptId,
            @RequestParam Long positionId,
            HttpSession session) {

        MemberSession ms = (MemberSession) session.getAttribute("memberSession");
        if (ms == null)
            return "redirect:/member/login";

        // Service에서 회원 정보(부서, 직급) 업데이트 로직 수행
        memberService.updateMemberInfo(memberId, ms.getMemberId(), deptId, positionId);

        return "redirect:/admin/memberList";
    }

    // 퇴사 처리
    @PostMapping("/fire/{id}")
    public String fireMember(@PathVariable("id") Long memberId, HttpSession session) {
        MemberSession ms = (MemberSession) session.getAttribute("memberSession");
        if (ms == null)
            return "redirect:/member/login";

        // Service에서 회원을 삭제하거나 Status를 'LEAVE'로 변경
        memberService.fireMember(memberId, ms.getMemberId());

        return "redirect:/admin/memberList";
    }
}
