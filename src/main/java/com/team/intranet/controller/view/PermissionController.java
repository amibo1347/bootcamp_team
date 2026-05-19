package com.team.intranet.controller.view;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.dto.PositionDto;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.member.Role;
import com.team.intranet.enums.member.Status;
import com.team.intranet.enums.member.SubAdminPermission;
import com.team.intranet.service.MemberService;
import com.team.intranet.service.PositionService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 권한 관리 페이지 (ADMIN 전용).
 *  - 회사의 모든 직급을 보여주고, SUB_ADMIN 직급에 대해서만 세부 권한 체크박스를 토글한다.
 *  - 권한의 적용은 직급 단위로 일괄 적용된다. (MEMBER 단위가 아님)
 *  - MASTER 는 시스템 관리자라 기업 내 권한 관리에는 관여하지 않으므로 hasRole('ADMIN') 만 허용.
 */
@Controller
@RequestMapping("/admin/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final PositionService positionService;
    private final MemberService memberService;

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public String permissionList(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                                  Model model) {
        if (ms == null) return "redirect:/member/login";

        // 대표(Role.ADMIN) 직급은 권한 관리 대상에서 제외 — 항상 모든 권한을 가지므로 토글할 의미가 없음.
        List<PositionDto> positions = positionService.findAllLevelDesc(ms.getCompanyId())
            .stream()
            .filter(p -> p.getRole() != Role.ADMIN)
            .map(PositionDto::fromEntity)
            .toList();

        // 회원별 예외 권한 부여 리스트: 활성(JOIN) 회원만 노출.
        List<Member> members = memberService.findMembersByStatus(ms.getCompanyId(), Status.JOIN);

        model.addAttribute("positions", positions);
        model.addAttribute("members", members);
        // 화면에서 "권한 정의"를 그릴 수 있도록 enum 전체를 같이 내려준다.
        model.addAttribute("allPermissions", SubAdminPermission.values());
        return "admin/managingPermission";
    }
}
