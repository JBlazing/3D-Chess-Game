package com.jblazing.game.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jblazing.engine.api.EngineProtocol;
import com.jblazing.engine.api.MoveReply;
import com.jblazing.engine.api.MoveRequest;
import com.jblazing.model.Move;
import com.jblazing.model.Result;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Duration;
import java.util.Optional;

/**
 * Asks the chess engine service to play moves, over ActiveMQ (see {@link EngineProtocol}): each move
 * is a request on the moves queue, and this waits for the reply. A request nobody has taken by the
 * time this gives up expires on the broker, so an engine never plays a move nobody is waiting for.
 */
@Component
public class ChessEngineClient {

    private static final Logger logger = LoggerFactory.getLogger(ChessEngineClient.class);

    private final JmsTemplate jms;
    private final MessageConverter converter;
    private final Duration timeout;

    /** The engine failed, timed out or can't be reached; nothing is wrong with the move, so the player can try again. */
    @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "The chess engine is busy, try again")
    public static class EngineUnavailableException extends RuntimeException {
        EngineUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public ChessEngineClient(ConnectionFactory connectionFactory, ObjectMapper objectMapper,
                             @Value("${game.chess.engine.timeout:PT10S}") Duration timeout) {
        this.converter = EngineProtocol.messageConverter(objectMapper);
        this.timeout = timeout;
        this.jms = new JmsTemplate(connectionFactory);
        jms.setReceiveTimeout(timeout.toMillis());
        jms.setExplicitQosEnabled(true);
        jms.setTimeToLive(timeout.toMillis());
        // a request is only worth anything while someone waits for its reply: no need to keep it on disk
        jms.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
    }

    /**
     * Plays the move (or, with a null move, a computer move) on the position.
     *
     * @return the result, or empty when the move is illegal
     * @throws EngineUnavailableException when the engine fails or doesn't answer in time
     */
    public Optional<Result> move(Move move, String boardState) {
        MoveRequest request = new MoveRequest(boardState, move);
        Message replyMessage;
        try {
            replyMessage = jms.sendAndReceive(EngineProtocol.MOVES_QUEUE, session -> converter.toMessage(request, session));
        } catch (JmsException e) {
            logger.error("Could not reach the chess engine", e);
            throw new EngineUnavailableException("Could not reach the chess engine", e);
        }
        if (replyMessage == null) {
            logger.error("The chess engine didn't answer in time ({})", timeout);
            throw new EngineUnavailableException("The chess engine didn't answer in time", null);
        }
        MoveReply reply;
        try {
            reply = (MoveReply) converter.fromMessage(replyMessage);
        } catch (JMSException | ClassCastException e) {
            throw new EngineUnavailableException("Unreadable reply from the chess engine", e);
        }
        return switch (reply.outcome()) {
            case PLAYED -> Optional.of(reply.result());
            case ILLEGAL -> Optional.empty();
            case FAILED -> {
                logger.error("The chess engine failed: {}", reply.error());
                throw new EngineUnavailableException("The chess engine failed: " + reply.error(), null);
            }
        };
    }
}
