package com.team.intranet.config;

import com.team.intranet.entity.Member;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.session.MemberSession;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final MemberRepository memberRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, 
                                        Authentication authentication) throws IOException, ServletException {
        
        // 1. 로그인한 사용자 정보 가져오기
        String loginId = authentication.getName();
        Member member = memberRepository.findByLoginId(loginId).orElseThrow();

        // 2. 태호님이 만든 MemberSession 객체 생성 및 세션 저장
        MemberSession ms = new MemberSession(member);
        HttpSession session = request.getSession();
        session.setAttribute("memberSession", ms);
        System.out.println("이미지주소: " + member.getProfileImgUrl());

        // 3. 성공 후 메인 페이지로 이동
        response.sendRedirect("/index");
    }
}