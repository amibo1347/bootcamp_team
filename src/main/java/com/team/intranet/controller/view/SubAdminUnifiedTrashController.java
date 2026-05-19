package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@RequestMapping("/subAdmin")
public class SubAdminUnifiedTrashController {

    /**
     * 통합 휴지통 페이지.
     * ※ 모든 인증된 회원이 진입 가능 — 일반 사용자는 본인 작성 글의 삭제분만 볼 수 있고,
     *   TRASH_MANAGEMENT 권한 보유자만 회사 전체 삭제 글을 볼 수 있다 (분기는 ArticleService 에서).
     */
    @GetMapping("/unified-trash")
    @PreAuthorize("isAuthenticated()")
    public String unifiedTrashPage() {
        return "subAdmin/unifiedTrash";
    }
}
