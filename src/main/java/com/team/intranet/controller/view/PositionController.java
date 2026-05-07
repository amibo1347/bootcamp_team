package com.team.intranet.controller.view;

import com.team.intranet.dto.PositionDto;
import com.team.intranet.service.PositionService;
import com.team.intranet.session.MemberSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.List;

@Controller
@RequestMapping("/admin/position")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    // 직급 관리 메인 페이지 (목록 조회)
    @GetMapping("/list")
    public String positionList(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                               Model model) {
        if (ms == null) return "redirect:/member/login";

        List<PositionDto> positions = positionService.findAllLevelDesc(ms.getCompanyId())
            .stream().map(PositionDto::fromEntity)
            .toList();
        model.addAttribute("companyId", ms.getCompanyId());
        model.addAttribute("memberRole", ms.getRole());
        model.addAttribute("positions", positions);
        return "admin/managingPosition";
    }
}