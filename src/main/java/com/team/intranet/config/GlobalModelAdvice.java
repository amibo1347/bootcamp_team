package com.team.intranet.config;

import com.team.intranet.dto.BoardDto;
import com.team.intranet.service.BoardService;
import com.team.intranet.service.MemberService;
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
    
    @ModelAttribute("logoPath")
    public String logoPath(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null || ms.getCompanyId() == null) {
            return null;
        }
        return memberService.getLogoPath(ms.getCompanyId());
    }
    
    @ModelAttribute("boardList")
    public List<BoardDto> boardList(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null || ms.getCompanyId() == null) {
            return Collections.emptyList();
        }
        return boardService.findVisibleBoards(ms);
    }
}