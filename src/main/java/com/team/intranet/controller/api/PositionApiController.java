package com.team.intranet.controller.api;

import com.team.intranet.dto.PositionDto;
import com.team.intranet.service.PositionService;
import com.team.intranet.session.MemberSession;
import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@RequestMapping("/api/admin/position")
@RequiredArgsConstructor
public class PositionApiController {

    private final PositionService positionService;

    // 직급 생성 처리
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
    public String createPosition(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @RequestBody PositionDto dto, Model model) {

        positionService.createPosition(ms, dto);

        List<PositionDto> positionList = positionService.findAllLevelDesc(ms.getCompanyId())
                .stream().map(PositionDto::fromEntity)
                .toList();
        model.addAttribute("positions", positionList);
        return "admin/managingPosition :: positionListContainer";
    }

    // 직급 수정 처리
    @PostMapping("/update/{positionId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
    public String updatePosition(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @PathVariable Long positionId,
            @RequestBody PositionDto positionDto, Model model) {



        positionService.updatePosition(ms, positionDto, positionId);
        
        List<PositionDto> positionList = positionService.findAllLevelDesc(ms.getCompanyId())
            .stream().map(PositionDto::fromEntity)
             .toList();

        model.addAttribute("positions", positionList);
        return "admin/managingPosition :: positionListContainer";
        
        }

    @PostMapping("/delete/{positionId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUB_ADMIN')")
        public String deletePosition(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @PathVariable Long positionId, Model model) {

        positionService.deletePosition(ms, positionId);

        List<PositionDto> positionList = positionService.findAllLevelDesc(ms.getCompanyId())
            .stream().map(PositionDto::fromEntity)
            .toList();
        model.addAttribute("positions", positionList);
        return "admin/managingPosition :: positionListContainer";
        }
    }