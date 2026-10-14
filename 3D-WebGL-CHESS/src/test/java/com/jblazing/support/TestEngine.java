package com.jblazing.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jblazing.engine.JavaScriptEngine;
import com.jblazing.engine.MoveListener;
import com.jblazing.engine.api.EngineProtocol;
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.Map;

/**
 * The real chess engine for the tests, started once for the whole test run: an ActiveMQ broker inside
 * the test JVM, and the engine's listener on it. Only the engine's own beans, not its Spring Boot app,
 * which here would also pick up the web app's auto-configuration (database, Redis...) from the classpath.
 */
public final class TestEngine {

    private static final String BROKER_NAME = "chess-test";
    public static final String BROKER_URL = "vm://" + BROKER_NAME + "?create=false";

    private static AnnotationConfigApplicationContext engine;

    private TestEngine() {
    }

    @Configuration
    @EnableJms
    static class EngineConfig {
        @Bean
        ConnectionFactory connectionFactory() {
            return new ActiveMQConnectionFactory(BROKER_URL);
        }

        @Bean
        DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
            DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
            factory.setConnectionFactory(connectionFactory);
            factory.setMessageConverter(EngineProtocol.messageConverter(new ObjectMapper()));
            return factory;
        }
    }

    public static synchronized void start() {
        if (engine != null) {
            return;
        }
        try {
            BrokerService broker = new BrokerService();
            broker.setBrokerName(BROKER_NAME);
            broker.setPersistent(false);
            broker.setUseJmx(false);
            broker.start();
            broker.waitUntilStarted();
        } catch (Exception e) {
            throw new IllegalStateException("Could not start the test ActiveMQ broker", e);
        }
        engine = new AnnotationConfigApplicationContext(EngineConfig.class, JavaScriptEngine.class, MoveListener.class);
        engine.registerShutdownHook();
    }

    /** Properties pointing the web app at the test broker, starting it and the engine if need be. */
    public static Map<String, Object> properties() {
        start();
        // the names application.properties reads; the test broker has no users: connect anonymously
        return Map.of("ACTIVEMQ_URL", BROKER_URL, "ACTIVEMQ_USER", "", "ACTIVEMQ_PASSWORD", "");
    }

    public static void useEngine(DynamicPropertyRegistry registry) {
        properties().forEach((name, value) -> registry.add(name, () -> value));
    }
}
