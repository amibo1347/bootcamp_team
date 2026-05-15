package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.team.intranet.enums.ErrorCode;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.session.MemberSession;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 결재 양식 관리 페이지 (ADMIN/MASTER 전용).
 * 진입: GET /approval/templates
 *
 * 권한 검사: memberSession.isAdminOrMaster() — SUB_ADMIN 은 배제.
 * 일반 사용자/SUB_ADMIN 가 직접 URL 로 들어오면 NO_AUTHORITY.
 */
@Controller
@RequiredArgsConstructor
public class ApprovalTemplateViewController {

    @GetMapping("/approval/templates")
    public String templateManage(HttpSession session) {
        Object raw = session.getAttribute("memberSession");
        if (!(raw instanceof MemberSession ms) || !ms.isAdminOrMaster()) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
        return "approval/admin/template-manage";
    }
}
