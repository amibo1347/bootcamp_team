package com.team.intranet.config;

import com.team.intranet.dto.BoardDto;
import com.team.intranet.entity.SystemNotice;
import com.team.intranet.service.BoardService;
import com.team.intranet.service.MemberService;
import com.team.intranet.service.SystemNoticeService;
import com.team.intranet.session.MemberSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.Collections;
import java.util.List;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {
    
    private final MemberService memberService;
    private final BoardService boardService;
    private final SystemNoticeService systemNoticeService;

    /** 현재 노출 대상 시스템 공지 — 전 회사 회원 화면 배너용. 없으면 null. */
    @ModelAttribute("systemNotice")
    public SystemNotice systemNotice() {
        return systemNoticeService.findActiveNotice();
    }
    
    @ModelAttribute("logoPath")
    public String logoPath(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null || ms.getCompanyId() == null) {
            return null;
        }
        return memberService.getLogoPath(ms.getCompanyId());
    }
    
    // 게시판 사용자
    @ModelAttribute("boardList")
    public List<BoardDto> boardList(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null || ms.getCompanyId() == null) {
            return Collections.emptyList();
        }
        return boardService.findVisibleBoards(ms);
    }
}