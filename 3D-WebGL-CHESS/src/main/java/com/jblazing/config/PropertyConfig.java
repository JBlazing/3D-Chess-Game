package com.jblazing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

@Configuration
@PropertySource("file:///etc/jblazing/chess.properties")
public class PropertyConfig {

    @Bean
    public String configureGlobal(Environment env) throws Exception {
        String property = env.getProperty("spring.security.oauth2.client.registration.google.client-id", String.class);
        return property;
    }

}
