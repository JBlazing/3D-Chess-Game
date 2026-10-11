package com.jblazing.controller;

import com.jblazing.user.UserAccount;
import com.jblazing.user.UserAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;

/**
 * The signed-in user's account. The OAuth2 code exchange itself is handled by Spring Security
 * (/oauth2/authorization/google -> Google -> /login/oauth2/code/google).
 */
@RestController
@RequestMapping(path = "/auth")
public class AuthController {

    private final UserAccountService userAccountService;

    public AuthController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    public record UsernameRequest(String username) {
    }

    public record ProfileRequest(String name, String picture) {
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

    /** Whether a username is valid and free for the signed-in user, for checking as it is typed. */
    @GetMapping("username-available")
    public Map<String, Object> usernameAvailable(OAuth2AuthenticationToken authentication, @RequestParam("username") String username) {
        Long accountId = authentication == null ? null : account(authentication).getId();
        return Map.of("available", userAccountService.usernameAvailable(accountId, username));
    }

    /** Sets or changes the user's username. */
    @PutMapping("user/username")
    public Map<String, Object> setUsername(OAuth2AuthenticationToken authentication, @RequestBody UsernameRequest request) {
        return toJson(userAccountService.setUsername(account(authentication).getId(), request.username()));
    }

    /** Updates the user's name and picture. */
    @PutMapping("user/profile")
    public Map<String, Object> updateProfile(OAuth2AuthenticationToken authentication, @RequestBody ProfileRequest request) {
        return toJson(userAccountService.updateProfile(account(authentication).getId(), request.name(), request.picture()));
    }

    private UserAccount account(OAuth2AuthenticationToken authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in");
        }
        return userAccountService.find(authentication.getAuthorizedClientRegistrationId(), authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No account"));
    }

    private static Map<String, Object> toJson(UserAccount account) {
        return Map.of(
                "id", account.getId(),
                "username", Objects.toString(account.getUsername(), ""),
                "name", Objects.toString(account.getName(), ""),
                "email", Objects.toString(account.getEmail(), ""),
                "picture", Objects.toString(account.getPictureUrl(), ""),
                "rating", account.getRating(),
                "ratedGames", account.getRatedGames());
    }
}
