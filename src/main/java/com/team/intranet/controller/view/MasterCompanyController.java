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

    /** 회사 생성 (초기 ADMIN 동시 생성). 로고는 선택 — 업로드 안 하면 회사만 생성. */
    @PostMapping
    public String create(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                         @RequestParam(name = "companyName", required = false) String companyName,
                         @RequestParam(name = "companyDomain", required = false) String companyDomain,
                         @RequestParam(name = "usesEmployeeNo", defaultValue = "false") boolean usesEmployeeNo,
                         @RequestParam(name = "adminLoginId", required = false) String adminLoginId,
                         @RequestParam(name = "adminName", required = false) String adminName,
                         @RequestParam(name = "adminEmail", required = false) String adminEmail,
                         @RequestParam(name = "logoFile", required = false) MultipartFile logoFile,
                         Model model, RedirectAttributes redirectAttributes) throws IOException {
        if (master == null) return "redirect:/master/login";

        String error = validateCreate(companyName, adminLoginId, adminName);
        byte[] logoBytes = null;
        if (error == null && logoFile != null && !logoFile.isEmpty()) {
            String contentType = logoFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                error = "로고는 이미지 파일만 업로드할 수 있습니다.";
            } else {
                logoBytes = logoFile.getBytes();
            }
        }
        if (error == null) {
            try {
                String tempPassword = companyService.create(companyName, companyDomain, usesEmployeeNo,
                        adminLoginId, adminName, adminEmail, logoBytes);
                redirectAttributes.addFlashAttribute("successMessage",
                        "회사가 생성되었습니다: " + companyName.trim()
                        + " — 대표 계정 아이디: " + adminLoginId.trim()
                        + ", 초기 비밀번호: " + tempPassword);
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
        model.addAttribute("formUsesEmployeeNo", usesEmployeeNo);
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
        model.addAttribute("companyAdmin", companyService.findCompanyAdmin(id));
        model.addAttribute("delegationCandidates", companyService.listDelegationCandidates(id));
        return "master/company-edit";
    }

    /** 회사 정보(이름/도메인) 수정 처리. */
    @PostMapping("/{id}")
    public String update(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                         @PathVariable Long id,
                         @RequestParam(name = "companyName", required = false) String companyName,
                         @RequestParam(name = "companyDomain", required = false) String companyDomain,
                         @RequestParam(name = "usesEmployeeNo", defaultValue = "false") boolean usesEmployeeNo,
                         RedirectAttributes redirectAttributes) {
        if (master == null) return "redirect:/master/login";
        if (companyName == null || companyName.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "회사명을 입력하세요.");
            return "redirect:/master/companies/" + id;
        }
        try {
            companyService.updateInfo(id, companyName, companyDomain, usesEmployeeNo);
            redirectAttributes.addFlashAttribute("successMessage", "회사 정보가 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
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

    /** 회사 대표(ADMIN) 비밀번호 초기화. 발급된 임시 비밀번호를 안내 메시지로 표시. */
    @PostMapping("/{id}/admin/reset-password")
    public String resetAdminPassword(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                                     @PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (master == null) return "redirect:/master/login";
        try {
            CompanyService.AdminPasswordReset result = companyService.resetAdminPassword(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "대표 계정(" + result.loginId() + ") 비밀번호를 초기화했습니다. "
                    + "임시 비밀번호: " + result.tempPassword() + " " +result.companyName() + " 대표에게 전달하세요.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/master/companies/" + id;
    }

    /** 회사 대표(ADMIN) 위임 — 같은 회사의 다른 재직 회원에게 대표직을 넘긴다. */
    @PostMapping("/{id}/admin/delegate")
    public String delegateAdmin(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                                @PathVariable Long id,
                                @RequestParam(name = "newAdminMemberId", required = false) Long newAdminMemberId,
                                RedirectAttributes redirectAttributes) {
        if (master == null) return "redirect:/master/login";
        if (newAdminMemberId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "위임할 직원을 선택하세요.");
            return "redirect:/master/companies/" + id;
        }
        try {
            CompanyService.AdminDelegation result = companyService.delegateAdmin(id, newAdminMemberId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "대표직을 " + result.newAdminName() + "(" + result.newAdminLoginId() + ")님에게 위임했습니다. "
                    + "기존 대표(" + result.oldAdminLoginId() + ")는 일반 직원으로 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
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

    /** 회사 생성 입력값 검증. 문제가 없으면 null. (초기 비밀번호는 서버 자동 생성이라 검증 대상 아님) */
    private String validateCreate(String companyName, String adminLoginId, String adminName) {
        if (isBlank(companyName) || isBlank(adminLoginId) || isBlank(adminName)) {
            return "회사명, 대표 아이디·이름을 모두 입력하세요.";
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
