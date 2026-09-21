package com.hcmute.springlab.controller;

import com.hcmute.springlab.security.CurrentUserService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentUserModelAdvice {

    private final CurrentUserService currentUserService;

    public CurrentUserModelAdvice(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @ModelAttribute("currentUser")
    public Object currentUser() {
        return currentUserService.getCurrentUser().orElse(null);
    }
}
