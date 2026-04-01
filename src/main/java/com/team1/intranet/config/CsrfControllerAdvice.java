package com.team1.intranet.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CsrfControllerAdvice {

    @ModelAttribute
    public void addCsrfToken(Model model, HttpServletRequest request) {
        Object token = request.getAttribute("_csrf");
        if (token instanceof CsrfToken) {
            model.addAttribute("_csrf", token);
        }
    }
}
