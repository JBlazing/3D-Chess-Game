package com.jreynolds.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

@RestController
public class BaseController {

    private final String indexHtml;

    public BaseController() throws URISyntaxException, IOException {
        this.indexHtml = Files.readString(Paths.get(getClass().getResource("/static/index.html").toURI()));
    }

    @GetMapping("/")
    public String index() {
        return indexHtml;
    }


}
