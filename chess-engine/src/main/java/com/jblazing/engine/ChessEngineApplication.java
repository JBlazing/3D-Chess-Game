package com.jblazing.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jblazing.engine.api.EngineProtocol;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.support.converter.MessageConverter;

/**
 * The chess engine service: no web server, just a listener on the moves queue ({@link MoveListener}).
 * Its settings are in chess-engine.properties, not application.properties, so they can't be confused
 * with the web app's when both are on one classpath (the web app's tests run a real engine).
 */
@SpringBootApplication
public class ChessEngineApplication {

    public static final String CONFIG_NAME = "chess-engine";

    public static void main(String[] args) {
        builder().run(args);
    }

    public static SpringApplicationBuilder builder() {
        return new SpringApplicationBuilder(ChessEngineApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.name=" + CONFIG_NAME);
    }

    /** Spring Boot hands this to the listener container, for requests and replies alike. */
    @Bean
    public MessageConverter engineMessageConverter(ObjectMapper objectMapper) {
        return EngineProtocol.messageConverter(objectMapper);
    }
}
