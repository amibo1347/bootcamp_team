package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.service.MasterAccountService;
import com.team.intranet.session.MasterSession;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 본인 계정 관리 화면.
 *  - 접근 제어: masterSecurityFilterChain + MasterTotpGuardFilter (2차 인증까지 통과해야 진입).
 *  - 현재는 비밀번호 변경만 제공.
 */
@Controller
@RequestMapping("/master/account")
@RequiredArgsConstructor
public class MasterAccountController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final MasterAccountService masterAccountService;

    /** 계정 관리 화면. */
    @GetMapping({"", "/"})
    public String accountPage(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                              Model model) {
        if (master == null) return "redirect:/master/login";
        model.addAttribute("master", master);
        return "master/account";
    }

    /** 비밀번호 변경 처리. */
    @PostMapping("/password")
    public String changePassword(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                                 @RequestParam(name = "currentPassword", required = false) String currentPassword,
                                 @RequestParam(name = "newPassword", required = false) String newPassword,
                                 @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
                                 Model model) {
        if (master == null) return "redirect:/master/login";
        model.addAttribute("master", master);

        String error = validate(currentPassword, newPassword, confirmPassword);
        if (error != null) {
            model.addAttribute("errorMessage", error);
            return "master/account";
        }

        try {
            masterAccountService.changePassword(master.getMasterAdminId(), currentPassword, newPassword);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "master/account";
        }

        model.addAttribute("successMessage", "비밀번호가 변경되었습니다.");
        return "master/account";
    }

    /** 입력값 검증. 문제가 없으면 null, 있으면 사용자 메시지를 반환. */
    private String validate(String current, String next, String confirm) {
        if (isBlank(current) || isBlank(next) || isBlank(confirm)) {
            return "모든 항목을 입력하세요.";
        }
        if (next.length() < MIN_PASSWORD_LENGTH) {
            return "새 비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.";
        }
        if (!next.equals(confirm)) {
            return "새 비밀번호와 확인이 일치하지 않습니다.";
        }
        if (next.equals(current)) {
            return "새 비밀번호가 현재 비밀번호와 같습니다.";
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
