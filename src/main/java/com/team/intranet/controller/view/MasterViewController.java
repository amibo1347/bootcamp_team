package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.session.MasterSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * MASTER 콘솔 화면 컨트롤러.
 *  - 접근 제어는 masterSecurityFilterChain(@Order(1))이 담당. /master/login 만 비인증 허용.
 *  - 회사 목록/생성 등 실제 운영 기능은 후속 PR(PR 2)에서 추가.
 */
@Controller
@RequestMapping("/master")
public class MasterViewController {

    /** MASTER 로그인 페이지. 직전 로그인 실패 메시지가 세션에 있으면 모델로 전달. */
    @GetMapping("/login")
    public String loginForm(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession();
        Object loginError = session.getAttribute("masterLoginError");
        if (loginError != null) {
            model.addAttribute("errorMessage", loginError.toString());
            session.removeAttribute("masterLoginError"); // 1회성 — 새로고침 시 재노출 방지
        }
        return "master/login";
    }

    /** MASTER 콘솔 진입점 (현재는 자리표시 대시보드). */
    @GetMapping({"", "/"})
    public String dashboard(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                            Model model) {
        model.addAttribute("master", master);
        return "master/dashboard";
    }
}
