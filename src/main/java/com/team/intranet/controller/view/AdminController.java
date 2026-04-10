package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import com.team.intranet.service.MemberService;
import com.team.intranet.session.MemberSession;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

/////////////////////////////////////
/// 의존성 주입
    private MemberService memberService;

/////////////////////////////////////
/// 컨트롤러

    // 가입 승인용 포스트매핑
    @PostMapping("/accept/{id}")
    public String approveMember(
            @PathVariable("id") Long memberId, // 누구를?
            @RequestParam Long deptId, // 어떤 부서로?
            @RequestParam Long positionId, // 어떤 직급으로?
            HttpSession session // 관리자 확인용
    ) {
        // 세션 정보 추출
        MemberSession ms = (MemberSession) session.getAttribute("member");

        if (ms == null) {
            return "redirect:/login"; // 세션 만료 시 로그인 페이지로
        }

        String adminId = ms.getLoginId();

        // MemberService에 승인 로직 위임
        memberService.acceptMember(memberId, adminId, deptId, positionId);
        return "redirect:/admin/pending-list";
    }
}
