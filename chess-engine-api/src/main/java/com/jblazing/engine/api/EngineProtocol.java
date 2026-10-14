package com.jblazing.engine.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import java.util.Map;

/**
 * How the web app and the engine talk over ActiveMQ: request/reply. The web app sends a
 * {@link MoveRequest} to {@link #MOVES_QUEUE} with a reply-to destination; one engine instance takes
 * it (several can share the queue) and sends the {@link MoveReply} there. Both are JSON text
 * messages, their type named in the {@link #TYPE_PROPERTY} property.
 */
public final class EngineProtocol {

    public static final String MOVES_QUEUE = "chess.engine.moves";
    public static final String TYPE_PROPERTY = "_type";

    private EngineProtocol() {
    }

    /** Writes and reads the messages; both sides must use it. */
    public static MessageConverter messageConverter(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter(objectMapper);
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName(TYPE_PROPERTY);
        // short names rather than class names, so the classes can move without breaking the other side
        converter.setTypeIdMappings(Map.of("move-request", MoveRequest.class, "move-reply", MoveReply.class));
        return converter;
    }
}
