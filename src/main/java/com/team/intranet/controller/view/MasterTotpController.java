package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.team.intranet.entity.MasterAdmin;
import com.team.intranet.repository.MasterAdminRepository;
import com.team.intranet.service.MasterTotpService;
import com.team.intranet.session.MasterSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * MASTER 2차 인증(TOTP) 화면.
 *  - 비밀번호 통과 후 진입. 세션의 "masterPendingLoginId" 로 대상 MASTER 를 식별한다.
 *  - 미등록 → /setup(QR 등록), 등록됨 → /verify(코드 입력). TOTP 통과 시에만 masterSession 부여.
 */
@Controller
@RequestMapping("/master/totp")
@RequiredArgsConstructor
public class MasterTotpController {

    private final MasterAdminRepository masterAdminRepository;
    private final MasterTotpService totpService;

    /** 진입점 — 등록 여부에 따라 setup / verify 로 분기. */
    @GetMapping({"", "/"})
    public String dispatch(HttpServletRequest request) {
        MasterAdmin master = pendingMaster(request);
        if (master == null) return "redirect:/master/login";
        return master.hasTotp() ? "redirect:/master/totp/verify" : "redirect:/master/totp/setup";
    }

    /** TOTP 최초 등록 화면 — QR 코드 표시. */
    @GetMapping("/setup")
    public String setupForm(HttpServletRequest request, Model model) {
        MasterAdmin master = pendingMaster(request);
        if (master == null) return "redirect:/master/login";
        if (master.hasTotp()) return "redirect:/master/totp/verify";

        HttpSession session = request.getSession();
        String secret = (String) session.getAttribute("masterPendingTotpSecret");
        if (secret == null) {
            // QR 을 다시 그려도 같은 시크릿이 유지되도록 세션에 보관 (DB 저장은 검증 성공 후).
            secret = totpService.newSecret();
            session.setAttribute("masterPendingTotpSecret", secret);
        }
        model.addAttribute("qrImage", totpService.qrImageDataUri(secret, master.getLoginId()));
        model.addAttribute("secret", secret);
        return "master/totp-setup";
    }

    /** TOTP 등록 검증 — 성공 시 시크릿을 저장하고 콘솔 진입. */
    @PostMapping("/setup")
    public String setupSubmit(@RequestParam(name = "code", required = false) String code,
                              HttpServletRequest request, Model model) {
        MasterAdmin master = pendingMaster(request);
        if (master == null) return "redirect:/master/login";
        if (master.hasTotp()) return "redirect:/master/totp/verify";

        HttpSession session = request.getSession();
        String secret = (String) session.getAttribute("masterPendingTotpSecret");
        if (secret == null) return "redirect:/master/totp/setup";

        if (!totpService.verify(secret, code)) {
            model.addAttribute("qrImage", totpService.qrImageDataUri(secret, master.getLoginId()));
            model.addAttribute("secret", secret);
            model.addAttribute("errorMessage", "코드가 일치하지 않습니다. 인증기 앱의 6자리 코드를 다시 입력하세요.");
            return "master/totp-setup";
        }

        master.enrollTotp(secret);
        completeLogin(request, master);
        return "redirect:/master";
    }

    /** TOTP 코드 입력 화면 (이미 등록된 MASTER). */
    @GetMapping("/verify")
    public String verifyForm(HttpServletRequest request) {
        MasterAdmin master = pendingMaster(request);
        if (master == null) return "redirect:/master/login";
        if (!master.hasTotp()) return "redirect:/master/totp/setup";
        return "master/totp-verify";
    }

    /** TOTP 코드 검증 — 성공 시 콘솔 진입. */
    @PostMapping("/verify")
    public String verifySubmit(@RequestParam(name = "code", required = false) String code,
                               HttpServletRequest request, Model model) {
        MasterAdmin master = pendingMaster(request);
        if (master == null) return "redirect:/master/login";
        if (!master.hasTotp()) return "redirect:/master/totp/setup";

        if (!totpService.verify(master.getTotpSecret(), code)) {
            model.addAttribute("errorMessage", "코드가 일치하지 않습니다. 인증기 앱의 6자리 코드를 다시 입력하세요.");
            return "master/totp-verify";
        }

        completeLogin(request, master);
        return "redirect:/master";
    }

    /** 세션의 pending loginId 로 MASTER 를 조회. 없으면 null. */
    private MasterAdmin pendingMaster(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object loginId = session.getAttribute("masterPendingLoginId");
        if (loginId == null) return null;
        return masterAdminRepository.findByLoginIdIgnoreCase(loginId.toString()).orElse(null);
    }

    /** TOTP 통과 처리: 로그인 시각 기록 + masterSession 부여 + pending 정리. */
    private void completeLogin(HttpServletRequest request, MasterAdmin master) {
        master.recordLogin();
        masterAdminRepository.save(master); // recordLogin + (등록 시) enrollTotp 함께 영속화

        HttpSession session = request.getSession();
        session.setAttribute("masterSession", new MasterSession(master));
        session.removeAttribute("masterPendingLoginId");
        session.removeAttribute("masterPendingTotpSecret");
    }
}
