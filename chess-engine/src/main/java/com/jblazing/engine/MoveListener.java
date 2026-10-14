package com.jblazing.engine;

import com.jblazing.engine.api.EngineProtocol;
import com.jblazing.engine.api.MoveReply;
import com.jblazing.engine.api.MoveRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Plays the move requests sent to {@link EngineProtocol#MOVES_QUEUE}. The reply goes to the request's
 * reply-to destination with its correlation id (Spring JMS does both for a listener's return value).
 */
@Component
public class MoveListener {

    private static final Logger logger = LoggerFactory.getLogger(MoveListener.class);

    private final JavaScriptEngine engine;

    public MoveListener(JavaScriptEngine engine) {
        this.engine = engine;
    }

    @JmsListener(destination = EngineProtocol.MOVES_QUEUE, concurrency = "${chess.engine.concurrency:1-4}")
    public MoveReply play(MoveRequest request) {
        try {
            return engine.move(request.move(), request.boardState())
                    .map(MoveReply::played)
                    .orElseGet(MoveReply::illegal);
        } catch (RuntimeException e) {
            logger.error("Could not play {} from {}", request.move(), request.boardState(), e);
            return MoveReply.failed(e.toString());
        }
    }
}
