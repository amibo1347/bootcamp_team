package com.team.intranet.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

@Controller
public class MainController {

    @GetMapping({"/", "/index", ""})
    public String index() {
        return "index";
    }

    @GetMapping("/tables")
    public String tables() {
        return "basic-tables";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @GetMapping("/calendar")
    public String calendar() {
        return "calendar";
    }

    @GetMapping("/form-elements")
    public String formElements() {
        return "form-elements";
    }

    @GetMapping("/blank")
    public String blank() {
        return "blank";
    }

    @GetMapping("/settings")
    public String settings() {
        return "settings"; 
    }
}
