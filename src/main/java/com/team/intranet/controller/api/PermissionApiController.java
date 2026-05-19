package com.team.intranet.controller.api;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.enums.member.SubAdminPermission;
import com.team.intranet.service.MemberService;
import com.team.intranet.service.PositionService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 권한 관리 API (ADMIN 전용).
 *  - 직급 단위로 SUB_ADMIN 세부 권한 집합을 일괄 교체한다.
 *  - permissions 가 비어있으면 해당 직급의 모든 권한을 해제한다.
 */
@Controller
@RequestMapping("/api/admin/permission")
@RequiredArgsConstructor
public class PermissionApiController {

    private final PositionService positionService;
    private final MemberService memberService;

    @PostMapping("/{positionId}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updatePermissions(
            @PathVariable("positionId") Long positionId,
            @RequestParam(value = "permissions", required = false) List<SubAdminPermission> permissions,
            @SessionAttribute("memberSession") MemberSession ms) {

        Set<SubAdminPermission> set = (permissions == null || permissions.isEmpty())
            ? EnumSet.noneOf(SubAdminPermission.class)
            : EnumSet.copyOf(permissions);

        positionService.updatePermissions(ms, positionId, set);
        return ResponseEntity.ok().build();
    }

    /**
     * 회원별 예외 권한 일괄 교체. 인사이동과 동일한 다중 선택 패턴.
     *  - 선택된 회원 모두에게 동일한 권한 집합을 설정 (교체 의미).
     *  - permissions 가 비어있으면 해당 회원들의 모든 예외 권한 해제.
     */
    @PostMapping("/members")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateMemberExtraPermissions(
            @RequestParam("memberIds") List<Long> memberIds,
            @RequestParam(value = "permissions", required = false) List<SubAdminPermission> permissions,
            @SessionAttribute("memberSession") MemberSession ms) {

        Set<SubAdminPermission> set = (permissions == null || permissions.isEmpty())
            ? EnumSet.noneOf(SubAdminPermission.class)
            : EnumSet.copyOf(permissions);

        memberService.updateExtraPermissions(ms, memberIds, set);
        return ResponseEntity.ok().build();
    }
}
