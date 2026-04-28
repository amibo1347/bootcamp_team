package com.team.intranet.controller.api;

import com.team.intranet.dto.PositionDto;
import com.team.intranet.service.PositionService;
import com.team.intranet.session.MemberSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/api/admin/position")
@RequiredArgsConstructor
public class PositionApiController {

    private final PositionService positionService;

    // 직급 생성 처리
    @PostMapping("/create")
    public String createPosition(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                                 @ModelAttribute PositionDto positionDto,
                                 RedirectAttributes redirectAttributes) {
        try {
            positionService.createPosition(ms, positionDto);
            redirectAttributes.addFlashAttribute("message", "직급이 성공적으로 생성되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "직급 생성 실패: " + e.getMessage());
        }
        return "redirect:/admin/position/list";
    }

    // 직급 수정 처리
    @PostMapping("/update/{positionId}")
    public String updatePosition(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                                 @PathVariable Long positionId,
                                 @ModelAttribute PositionDto positionDto,
                                 RedirectAttributes redirectAttributes) {
        try {
            positionService.updatePosition(ms, positionDto, positionId);
            redirectAttributes.addFlashAttribute("message", "직급 정보가 수정되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "직급 수정 실패: " + e.getMessage());
        }
        return "redirect:/admin/position/list";
    }

    // 직급 삭제 처리
    @PostMapping("/delete/{positionId}")
    public String deletePosition(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                                 @PathVariable Long positionId,
                                 RedirectAttributes redirectAttributes) {
        try {
            positionService.deletePosition(ms, positionId);
            redirectAttributes.addFlashAttribute("message", "직급이 삭제되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "직급 삭제 실패: " + e.getMessage());
        }
        return "redirect:/admin/position/list";
    }
}
