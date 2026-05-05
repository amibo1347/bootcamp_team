package com.team.intranet.controller.api;

import com.team.intranet.dto.PositionDto;
import com.team.intranet.enums.member.Role;
import com.team.intranet.service.PositionService;
import com.team.intranet.session.MemberSession;
import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api/admin/position")
@RequiredArgsConstructor
public class PositionApiController {

    private final PositionService positionService;

    // 직급 생성 처리
    @PostMapping("/create")
    public String createPosition(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @RequestBody PositionDto dto, Model model) {
        if (ms == null || ms.getCompanyId() == null) {
            return "redirect:/member/login";
        }
        if (ms.getRole() == Role.USER) {
            return "redirect:/member/login";
        }
        positionService.createPosition(ms, dto);

        List<PositionDto> positionList = positionService.findAllByCompanyCompanyIdOrderByPositionLevelDESC(ms.getCompanyId())
                .stream().map(PositionDto::fromEntity)
                .toList();
        model.addAttribute("positions", positionList);
        return "admin/managingPosition :: positionListContainer";
    }

    // 직급 수정 처리
    @PostMapping("/update/{positionId}")
    public String updatePosition(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @PathVariable Long positionId,
            @RequestBody PositionDto positionDto, Model model) {

        if (ms == null || ms.getCompanyId() == null) {
            return "redirect:/member/login";
        }
        if (ms.getRole() == Role.USER) {
            return "redirect:/member/login";
        }

        positionService.updatePosition(ms, positionDto, positionId);
        
        List<PositionDto> positionList = positionService.findAllByCompanyCompanyIdOrderByPositionLevelDESC(ms.getCompanyId())
            .stream().map(PositionDto::fromEntity)
             .toList();

        model.addAttribute("positions", positionList);
        return "admin/managingPosition :: positionListContainer";
        
        }

        @PostMapping("/delete/{positionId}")
        public String deletePosition(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @PathVariable Long positionId, Model model) {
            
        if(ms == null || ms.getCompanyId() == null) {
            return "redirect:/member/login";
        }
        if(ms.getRole() == Role.USER) {
            return "redirect:/member/login";
        }

        positionService.deletePosition(ms, positionId);

        List<PositionDto> positionList = positionService.findAllByCompanyCompanyIdOrderByPositionLevelDESC(ms.getCompanyId())
            .stream().map(PositionDto::fromEntity)
            .toList();
        model.addAttribute("positions", positionList);
        return "admin/managingPosition :: positionListContainer";
        }
    }