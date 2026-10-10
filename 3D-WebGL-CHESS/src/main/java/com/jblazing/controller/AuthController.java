package com.jblazing.controller;

import com.jblazing.user.UserAccount;
import com.jblazing.user.UserAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * The OAuth2 code exchange itself is handled by Spring Security
 * (/oauth2/authorization/google -> Google -> /login/oauth2/code/google).
 */
@RestController
@RequestMapping(path = "/auth")
public class AuthController {

    private final UserAccountService userAccountService;

    public AuthController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("user")
    public ResponseEntity<Map<String, Object>> user(OAuth2AuthenticationToken authentication) {
        if (authentication == null) {
            return ResponseEntity.noContent().build();
        }
        return userAccountService.find(authentication.getAuthorizedClientRegistrationId(), authentication.getName())
                .map(AuthController::toJson)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private static Map<String, Object> toJson(UserAccount account) {
        return Map.of(
                "id", account.getId(),
                "name", Objects.toString(account.getName(), ""),
                "email", Objects.toString(account.getEmail(), ""),
                "picture", Objects.toString(account.getPictureUrl(), ""),
                "rating", account.getRating(),
                "ratedGames", account.getRatedGames());
    }
}
