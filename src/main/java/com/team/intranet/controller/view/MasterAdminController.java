package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.team.intranet.service.MasterAdminService;
import com.team.intranet.session.MasterSession;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 계정 관리 화면 — 목록 조회 + 신규 계정 생성.
 *  - 접근 제어: masterSecurityFilterChain + MasterTotpGuardFilter (2차 인증까지 통과한 MASTER 만).
 *  - 팀원에게 MASTER 계정을 발급하는 용도. 발급받은 팀원은 최초 로그인 시 본인 인증기를 등록한다.
 */
@Controller
@RequestMapping("/master/admins")
@RequiredArgsConstructor
public class MasterAdminController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final MasterAdminService masterAdminService;

    /** MASTER 계정 목록 + 생성 폼. */
    @GetMapping({"", "/"})
    public String list(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                       Model model) {
        if (master == null) return "redirect:/master/login";
        model.addAttribute("master", master);
        model.addAttribute("admins", masterAdminService.findAll());
        return "master/admins";
    }

    /** 신규 MASTER 계정 생성. */
    @PostMapping
    public String create(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                         @RequestParam(name = "loginId", required = false) String loginId,
                         @RequestParam(name = "name", required = false) String name,
                         @RequestParam(name = "email", required = false) String email,
                         @RequestParam(name = "password", required = false) String password,
                         @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
                         Model model, RedirectAttributes redirectAttributes) {
        if (master == null) return "redirect:/master/login";

        String error = validate(loginId, name, password, confirmPassword);
        if (error == null) {
            try {
                masterAdminService.create(loginId, name, email, password);
                redirectAttributes.addFlashAttribute("successMessage",
                        "MASTER 계정이 생성되었습니다: " + loginId.trim()
                        + " — 팀원에게 초기 비밀번호를 전달하세요. 첫 로그인 시 인증기 등록이 필요합니다.");
                return "redirect:/master/admins"; // PRG — 새로고침 재전송 방지
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
            }
        }

        // 실패 → 목록 + 입력값(비밀번호 제외) 유지하여 재렌더
        model.addAttribute("master", master);
        model.addAttribute("admins", masterAdminService.findAll());
        model.addAttribute("errorMessage", error);
        model.addAttribute("formLoginId", loginId);
        model.addAttribute("formName", name);
        model.addAttribute("formEmail", email);
        return "master/admins";
    }

    /** 입력값 검증. 문제가 없으면 null, 있으면 사용자 메시지를 반환. */
    private String validate(String loginId, String name, String password, String confirmPassword) {
        if (isBlank(loginId) || isBlank(name) || isBlank(password) || isBlank(confirmPassword)) {
            return "아이디, 이름, 비밀번호를 모두 입력하세요.";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.";
        }
        if (!password.equals(confirmPassword)) {
            return "비밀번호와 확인이 일치하지 않습니다.";
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
