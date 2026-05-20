package com.team.intranet.controller.view;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.team.intranet.service.CompanyService;
import com.team.intranet.session.MasterSession;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 의 회사 관리 화면.
 *  - 접근 제어: masterSecurityFilterChain + MasterTotpGuardFilter (2차 인증까지 통과한 MASTER 만).
 *  - 목록/검색, 생성(초기 ADMIN 동시), 정보 수정, 코드 재발급, 활성 토글.
 */
@Controller
@RequestMapping("/master/companies")
@RequiredArgsConstructor
public class MasterCompanyController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final CompanyService companyService;

    /** 회사 목록 + 검색 + 생성 폼. */
    @GetMapping({"", "/"})
    public String list(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                       @RequestParam(name = "q", required = false) String q, Model model) {
        if (master == null) return "redirect:/master/login";
        model.addAttribute("master", master);
        model.addAttribute("companies", companyService.list(q));
        model.addAttribute("q", q);
        return "master/companies";
    }

    /** 회사 생성 (초기 ADMIN 동시 생성). */
    @PostMapping
    public String create(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                         @RequestParam(name = "companyName", required = false) String companyName,
                         @RequestParam(name = "companyDomain", required = false) String companyDomain,
                         @RequestParam(name = "adminLoginId", required = false) String adminLoginId,
                         @RequestParam(name = "adminName", required = false) String adminName,
                         @RequestParam(name = "adminEmail", required = false) String adminEmail,
                         @RequestParam(name = "adminPassword", required = false) String adminPassword,
                         @RequestParam(name = "adminConfirmPassword", required = false) String adminConfirmPassword,
                         Model model, RedirectAttributes redirectAttributes) {
        if (master == null) return "redirect:/master/login";

        String error = validateCreate(companyName, adminLoginId, adminName, adminPassword, adminConfirmPassword);
        if (error == null) {
            try {
                companyService.create(companyName, companyDomain,
                        adminLoginId, adminName, adminEmail, adminPassword);
                redirectAttributes.addFlashAttribute("successMessage",
                        "회사가 생성되었습니다: " + companyName.trim()
                        + " — 대표 계정 아이디는 " + adminLoginId.trim() + " 입니다.");
                return "redirect:/master/companies"; // PRG
            } catch (IllegalArgumentException | IllegalStateException e) {
                error = e.getMessage();
            }
        }

        model.addAttribute("master", master);
        model.addAttribute("companies", companyService.list(null));
        model.addAttribute("errorMessage", error);
        model.addAttribute("formCompanyName", companyName);
        model.addAttribute("formCompanyDomain", companyDomain);
        model.addAttribute("formAdminLoginId", adminLoginId);
        model.addAttribute("formAdminName", adminName);
        model.addAttribute("formAdminEmail", adminEmail);
        return "master/companies";
    }

    /** 회사 정보 수정 화면. */
    @GetMapping("/{id}")
    public String editPage(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                           @PathVariable Long id, Model model) {
        if (master == null) return "redirect:/master/login";
        try {
            model.addAttribute("company", companyService.get(id));
        } catch (IllegalArgumentException e) {
            return "redirect:/master/companies";
        }
        model.addAttribute("master", master);
        model.addAttribute("hasLogo", companyService.hasLogo(id));
        return "master/company-edit";
    }

    /** 회사 정보(이름/도메인) 수정 처리. */
    @PostMapping("/{id}")
    public String update(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                         @PathVariable Long id,
                         @RequestParam(name = "companyName", required = false) String companyName,
                         @RequestParam(name = "companyDomain", required = false) String companyDomain,
                         RedirectAttributes redirectAttributes) {
        if (master == null) return "redirect:/master/login";
        if (companyName == null || companyName.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "회사명을 입력하세요.");
            return "redirect:/master/companies/" + id;
        }
        companyService.updateInfo(id, companyName, companyDomain);
        redirectAttributes.addFlashAttribute("successMessage", "회사 정보가 수정되었습니다.");
        return "redirect:/master/companies/" + id;
    }

    /** 회사 로고 이미지 업로드. */
    @PostMapping("/{id}/logo")
    public String uploadLogo(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                             @PathVariable Long id,
                             @RequestParam(name = "logoFile", required = false) MultipartFile logoFile,
                             RedirectAttributes redirectAttributes) throws IOException {
        if (master == null) return "redirect:/master/login";
        if (logoFile == null || logoFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "업로드할 이미지를 선택하세요.");
            return "redirect:/master/companies/" + id;
        }
        String contentType = logoFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            redirectAttributes.addFlashAttribute("errorMessage", "이미지 파일만 업로드할 수 있습니다.");
            return "redirect:/master/companies/" + id;
        }
        companyService.updateLogo(id, logoFile.getBytes());
        redirectAttributes.addFlashAttribute("successMessage", "로고를 업로드했습니다.");
        return "redirect:/master/companies/" + id;
    }

    /** 회사 로고 이미지 삭제. */
    @PostMapping("/{id}/logo/delete")
    public String deleteLogo(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                             @PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (master == null) return "redirect:/master/login";
        companyService.clearLogo(id);
        redirectAttributes.addFlashAttribute("successMessage", "로고를 삭제했습니다.");
        return "redirect:/master/companies/" + id;
    }

    /** 회사 코드 재발급. */
    @PostMapping("/{id}/code")
    public String reissueCode(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                              @PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (master == null) return "redirect:/master/login";
        String code = companyService.reissueCode(id);
        redirectAttributes.addFlashAttribute("successMessage", "회사 코드를 재발급했습니다: " + code);
        return "redirect:/master/companies/" + id;
    }

    /** 회사 활성/비활성 토글. */
    @PostMapping("/{id}/toggle")
    public String toggle(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                         @PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (master == null) return "redirect:/master/login";
        boolean active = companyService.toggleActive(id);
        redirectAttributes.addFlashAttribute("successMessage",
                active ? "회사를 활성화했습니다." : "회사를 비활성화했습니다.");
        return "redirect:/master/companies";
    }

    /** 회사 생성 입력값 검증. 문제가 없으면 null. */
    private String validateCreate(String companyName, String adminLoginId, String adminName,
                                  String password, String confirmPassword) {
        if (isBlank(companyName) || isBlank(adminLoginId) || isBlank(adminName)
                || isBlank(password) || isBlank(confirmPassword)) {
            return "회사명, 대표 아이디·이름·비밀번호를 모두 입력하세요.";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "대표 비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.";
        }
        if (!password.equals(confirmPassword)) {
            return "대표 비밀번호와 확인이 일치하지 않습니다.";
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
