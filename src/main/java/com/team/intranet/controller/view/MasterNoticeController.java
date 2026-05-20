package com.team.intranet.controller.view;

import java.time.LocalDateTime;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.team.intranet.enums.SystemNoticeType;
import com.team.intranet.service.SystemNoticeService;
import com.team.intranet.session.MasterSession;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 시스템 공지 / 점검 안내 관리.
 *  - 접근 제어: masterSecurityFilterChain + MasterTotpGuardFilter.
 *  - 목록 / 등록 / 삭제. (수정은 삭제 후 재등록으로 대체)
 */
@Controller
@RequestMapping("/master/notices")
@RequiredArgsConstructor
public class MasterNoticeController {

    private final SystemNoticeService systemNoticeService;

    @GetMapping({"", "/"})
    public String list(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                       Model model) {
        if (master == null) return "redirect:/master/login";
        model.addAttribute("master", master);
        model.addAttribute("notices", systemNoticeService.list());
        model.addAttribute("now", LocalDateTime.now());
        return "master/notices";
    }

    /** 공지 등록. */
    @PostMapping
    public String create(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                         @RequestParam(name = "content", required = false) String content,
                         @RequestParam(name = "noticeType", required = false) String noticeType,
                         @RequestParam(name = "startsAt", required = false) String startsAt,
                         @RequestParam(name = "endsAt", required = false) String endsAt,
                         RedirectAttributes redirectAttributes) {
        if (master == null) return "redirect:/master/login";

        if (content == null || content.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "공지 내용을 입력하세요.");
            return "redirect:/master/notices";
        }

        SystemNoticeType type;
        try {
            type = SystemNoticeType.valueOf(noticeType);
        } catch (Exception e) {
            type = SystemNoticeType.NOTICE;
        }

        LocalDateTime start = parseDateTime(startsAt);
        LocalDateTime end = parseDateTime(endsAt);
        if (start != null && end != null && end.isBefore(start)) {
            redirectAttributes.addFlashAttribute("errorMessage", "종료 시각이 시작 시각보다 빠릅니다.");
            return "redirect:/master/notices";
        }

        systemNoticeService.create(content, type, start, end);
        redirectAttributes.addFlashAttribute("successMessage", "공지를 등록했습니다.");
        return "redirect:/master/notices";
    }

    /** 공지 삭제. */
    @PostMapping("/{id}/delete")
    public String delete(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                         @PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (master == null) return "redirect:/master/login";
        systemNoticeService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "공지를 삭제했습니다.");
        return "redirect:/master/notices";
    }

    /** datetime-local 입력값(yyyy-MM-ddTHH:mm) 파싱. 비거나 형식 오류면 null. */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
