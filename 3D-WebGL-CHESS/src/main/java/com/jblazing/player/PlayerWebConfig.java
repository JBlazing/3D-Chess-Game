package com.jblazing.player;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class PlayerWebConfig implements WebMvcConfigurer {

    private final CurrentPlayerResolver currentPlayerResolver;

    public PlayerWebConfig(CurrentPlayerResolver currentPlayerResolver) {
        this.currentPlayerResolver = currentPlayerResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentPlayerResolver);
    }
}
