package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "signin";
    }

    @GetMapping("/tables")
    public String tables() {
        return "basic-tables";
    }

    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }
}
