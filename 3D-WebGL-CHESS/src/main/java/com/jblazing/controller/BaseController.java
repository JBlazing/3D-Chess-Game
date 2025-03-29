package com.jblazing.controller;

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

    public BaseController() throws URISyntaxException, IOException {
        this.loginHtml = Files.readString(Paths.get(getClass().getResource("/static/login.html").toURI()));
        this.indexHtml = Files.readString(Paths.get(getClass().getResource("/static/index.html").toURI()));
    }

    @GetMapping("/")
    public String login() {
        return loginHtml;
    }

    @GetMapping("/index")
    public String index() {
        return indexHtml;
    }


}
