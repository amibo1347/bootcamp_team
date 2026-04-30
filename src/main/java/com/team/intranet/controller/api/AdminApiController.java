package com.team.intranet.controller.api;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;

import com.team.intranet.service.MemberService;
import com.team.intranet.session.MemberSession;
import com.team.intranet.enums.member.Role;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/api/subAdmin")
@RequiredArgsConstructor
public class AdminApiController {

    private final MemberService memberService;

    // 가입 승인용 포스트매핑
    @PostMapping("/accept/{id}")
    public String approveMember(
            @PathVariable("id") Long memberId,
            @RequestParam Long deptId,
            @RequestParam Long positionId,
            @SessionAttribute("memberSession") MemberSession ms) {

        if (ms == null || ms.getRole() != Role.ADMIN) {
            return "redirect:/member/login";
        }

        memberService.acceptMember(memberId, ms.getMemberId(), deptId, positionId);
        return "redirect:/admin/waitingList";
    }

    // 가입 반려 (거절)
    @PostMapping("/reject/{id}")
    public String rejectMember(
            @PathVariable("id") Long memberId,
            @SessionAttribute("memberSession") MemberSession ms) {

        if (ms == null || ms.getRole() != Role.ADMIN) {
            return "redirect:/member/login";
        }

        memberService.rejectMember(memberId, ms.getMemberId());
        return "redirect:/admin/waitingList";
    }

    // 회원 정보 수정 (부서, 직급 변경)
    @PostMapping("/update/{id}")
    public String updateMember(
            @PathVariable("id") Long memberId,
            @RequestParam Long deptId,
            @RequestParam Long positionId,
            @RequestParam(value = "profileImg", required = false) MultipartFile profileImg,
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String phone,
            @RequestParam String birthDay,
            HttpSession session) throws IOException {
        
        LocalDate parsedBirthDay = null;
    if (birthDay != null && !birthDay.isBlank()) {
        parsedBirthDay = LocalDate.parse(birthDay); 
    }  
        MemberSession ms = (MemberSession) session.getAttribute("memberSession");
        if (ms == null || ms.getRole() != Role.ADMIN){
            return "redirect:/member/login";
        }

        byte[] imgBytes = (profileImg != null && !profileImg.isEmpty()) ? profileImg.getBytes() : null;
        
        memberService.updateMemberInfo(memberId, ms.getMemberId(), deptId, positionId, imgBytes, phone, email, name, parsedBirthDay);

        return "redirect:/admin/memberList";
    }

    // 퇴사 처리
    @PostMapping("/fire/{id}")
    public String fireMember(@PathVariable("id") Long memberId, HttpSession session) {
        MemberSession ms = (MemberSession) session.getAttribute("memberSession");
        if (ms == null || ms.getRole() != Role.ADMIN){
            return "redirect:/member/login";
        }

        memberService.fireMember(memberId, ms.getMemberId());

        return "redirect:/admin/memberList";
    }
}
