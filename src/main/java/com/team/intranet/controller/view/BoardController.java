package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.SessionAttribute;
import java.util.List;

import com.team.intranet.service.BoardService;
import com.team.intranet.service.DeptService;
import com.team.intranet.service.PositionService;
import com.team.intranet.session.MemberSession;
import com.team.intranet.dto.BoardDto;
import com.team.intranet.entity.Board;

@Controller
@RequestMapping("/admin/board")
@RequiredArgsConstructor
public class BoardController {
    
    private final BoardService boardService;
    private final DeptService deptService;
    private final PositionService positionService;

    // 게시판 관리 메인 페이지 (목록 조회)
    @GetMapping("/list")
    public String manageBoard(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            Model model) {

        if (ms == null || ms.getCompanyId() == null) {
            return "redirect:/member/login";
        }

        List<Board> boards = boardService.findAll(ms.getCompanyId());
        model.addAttribute("boards", boards);
        model.addAttribute("companyId", ms.getCompanyId());   
        model.addAttribute("positions", positionService.findAll(ms.getCompanyId()));
        model.addAttribute("departments", deptService.findAll(ms.getCompanyId()));
        return "admin/managingBoard";
    }

    @GetMapping("/{id}")
    public String viewBoard(@PathVariable Long id, MemberSession ms, Model model) {
    BoardDto board = boardService.findVisibleBoardById(ms, id);
    model.addAttribute("board", board);
    return "board/detail";
    }
}
