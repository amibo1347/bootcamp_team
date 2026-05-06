package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.SessionAttribute;
import java.util.List;

import com.team.intranet.service.BoardService;
import com.team.intranet.session.MemberSession;
import com.team.intranet.entity.Board;

@Controller
@RequestMapping("/admin/board")
@RequiredArgsConstructor
public class BoardController {
    
    private final BoardService boardService;

    @GetMapping("/list")
    public String manageBoard(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            Model model) {

        if (ms == null || ms.getCompanyId() == null) {
            return "redirect:/member/login";
        }

        List<Board> boards = boardService.findAll(ms.getCompanyId());
        model.addAttribute("boards", boards);
        model.addAttribute("companyId", ms.getCompanyId());       
        return "admin/managingBoard";
    }
}
