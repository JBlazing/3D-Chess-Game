package com.jblazing.controller;

import com.jblazing.auth.config.OAuthConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

@RestController
public class BaseController{

    private final String indexHtml;
    private final String loginHtml;
    private final boolean authEnabled;

    public BaseController(ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws URISyntaxException, IOException {
        this.loginHtml = Files.readString(Paths.get(getClass().getResource("/static/login.html").toURI()));
        this.indexHtml = Files.readString(Paths.get(getClass().getResource("/static/index.html").toURI()));
        this.authEnabled = clientRegistrations.getIfAvailable() != null;
    }

    @GetMapping("/")
    public ResponseEntity<String> login(@AuthenticationPrincipal OAuth2User user) {
        if (user != null || !authEnabled) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, OAuthConfig.HOME_PAGE)
                    .build();
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(loginHtml);
    }

    @GetMapping("/index")
    public ResponseEntity<String> index() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(indexHtml);
    }


}
