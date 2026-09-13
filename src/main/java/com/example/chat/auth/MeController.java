package com.example.chat.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/")
public class MeController {
    @GetMapping("/me")
    public String me(@AuthenticationPrincipal ChatUserPrincipal principal){
        return principal.email();
    }
}
