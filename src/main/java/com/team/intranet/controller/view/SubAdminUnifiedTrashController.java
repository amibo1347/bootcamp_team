package com.team.intranet.controller.view;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.enums.member.SubAdminPermission;
import com.team.intranet.session.MemberSession;

@Controller
@RequestMapping("/subAdmin")
public class SubAdminUnifiedTrashController {

    /**
     * 통합 휴지통 페이지.
     * ※ TRASH_MANAGEMENT 권한 보유자 또는 ADMIN/MASTER 만 진입.
     *   - SubAdminPermission 은 Spring Authority 가 아니라 MemberSession 의 자체 권한 집합이므로,
     *     @PreAuthorize 로 직접 검사 불가 → 메서드 진입 시 세션으로 확인.
     *   - 권한 없는 회원은 본인이 지운 글도 여기서 볼 수 없다 — 휴지통은 관리 도구로 분리.
     */
    @GetMapping("/unified-trash")
    @PreAuthorize("isAuthenticated()")
    public String unifiedTrashPage(@SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return "redirect:/company-login";
        if (!canAccessTrash(ms)) {
            // 권한 없으면 진입 불가 — 홈으로 (별도 에러 페이지를 만들 정도는 아님).
            return "redirect:/";
        }
        return "subAdmin/unifiedTrash";
    }

    /** ADMIN/MASTER 또는 TRASH_MANAGEMENT 권한 보유자. */
    private boolean canAccessTrash(MemberSession ms) {
        return ms.isAdminOrMaster() || ms.hasPermission(SubAdminPermission.TRASH_MANAGEMENT);
    }
}
