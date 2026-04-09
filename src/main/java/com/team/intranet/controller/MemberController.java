package com.team.intranet.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.team.intranet.dto.MemberDto;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.member.MemberType;
import com.team.intranet.service.MemberService;
import com.team.intranet.session.MemberSession;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/member") // 전체 매핑에 member 설정
@RequiredArgsConstructor
public class MemberController {
////////////////////////////////////////
/// 의존성 주입
    private final MemberService memberService;

////////////////////////////////////////
/// 비즈니스 로직

    @PostMapping("/signup") // 회원 가입
    public String newMember(MemberDto dto, Model model) {

        MemberType result = memberService.join(dto);

        switch(result){
            case JOIN_SUCCESS: // 가입 성공
                return "redirect:/signin";
            case ALEADY_MEMBER: // 이미 회원 정보가 있음
                return "redirect:/signup";
            case NOT_MATCH_PASSWORD: // 비밀번호가 일치하지 않음
                return "redirect:/signup";
            default :
                return "redirect:/signup";
        }
    }

    // 로그인
    @PostMapping("/login")
    public String login(@RequestParam String loginId, @RequestParam String password, HttpSession session, Model model){
        try {
        // 서비스에서 로그인 시도
        Member loginMember = memberService.login(loginId, password);

        // 세션에 필요한 정보 저장
        MemberSession ms = new MemberSession(loginMember);
        session.setAttribute("member", ms);

        return "redirect:/index"; // 로그인 성공 시 메인으로
        
    } catch (Exception e) {
        // 로그인 실패 시 에러 메시지를 담아 로그인 페이지로 다시 보냄
        model.addAttribute("loginError", e.getMessage());
        return "member/signin"; 
    }

    }
}
