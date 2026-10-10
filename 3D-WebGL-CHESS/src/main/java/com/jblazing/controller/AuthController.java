package com.jblazing.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The OAuth2 code exchange itself is handled by Spring Security
 * (/oauth2/authorization/google -> Google -> /login/oauth2/code/google).
 */
@RestController
@RequestMapping(path = "/auth")
public class AuthController {

    @GetMapping("user")
    public ResponseEntity<Map<String, String>> user(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(Map.of(
                "name", String.valueOf(user.getAttributes().getOrDefault("name", "")),
                "email", String.valueOf(user.getAttributes().getOrDefault("email", "")),
                "picture", String.valueOf(user.getAttributes().getOrDefault("picture", ""))));
    }
}
