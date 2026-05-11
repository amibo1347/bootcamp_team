package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@RequestMapping("/subAdmin")
public class SubAdminUnifiedTrashController {

    @GetMapping("/unified-trash")
    @PreAuthorize("hasRole('SUB_ADMIN') or hasRole('ADMIN') or hasRole('MASTER')")
    public String unifiedTrashPage() {
        return "subAdmin/unifiedTrash";
    }
}
