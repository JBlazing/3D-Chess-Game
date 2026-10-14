package com.jblazing.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jblazing.engine.api.EngineProtocol;
import com.jblazing.engine.api.MoveReply;
import com.jblazing.engine.api.MoveRequest;
import com.jblazing.model.Move;
import jakarta.jms.Message;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

/** The engine app answers move requests sent over ActiveMQ, as the web app sends them; on a broker inside the test JVM. */
class MoveListenerTest {

    private static final String BROKER_URL = "vm://engine-test?broker.persistent=false&broker.useJmx=false";
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static ConfigurableApplicationContext app;
    private static JmsTemplate jms;
    private static final MessageConverter converter = EngineProtocol.messageConverter(new ObjectMapper());

    @BeforeAll
    static void startEngine() {
        // as arguments, which come before chess-engine.properties; the test broker has no users: connect anonymously
        app = ChessEngineApplication.builder()
                .run("--spring.activemq.broker-url=" + BROKER_URL, "--spring.activemq.user=", "--spring.activemq.password=",
                        "--spring.main.banner-mode=off");
        jms = new JmsTemplate(new ActiveMQConnectionFactory(BROKER_URL));
        // a cold, interpreted GraalJS can take a while over its first moves
        jms.setReceiveTimeout(60_000);
    }

    @AfterAll
    static void stopEngine() {
        app.close();
    }

    private static MoveReply ask(MoveRequest request) throws Exception {
        Message reply = jms.sendAndReceive(EngineProtocol.MOVES_QUEUE, session -> converter.toMessage(request, session));
        assertThat(reply).as("a reply").isNotNull();
        return (MoveReply) converter.fromMessage(reply);
    }

    @Test
    void aLegalMoveIsPlayed() throws Exception {
        MoveReply reply = ask(new MoveRequest(START, new Move("e2", "e4")));

        assertThat(reply.outcome()).isEqualTo(MoveReply.Outcome.PLAYED);
        assertThat(reply.result().moveResult().get("san")).isEqualTo("e4");
        assertThat(reply.result().boardState()).startsWith("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b");
        assertThat(reply.result().status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void anIllegalMoveIsRefused() throws Exception {
        MoveReply reply = ask(new MoveRequest(START, new Move("e2", "e5")));

        assertThat(reply.outcome()).isEqualTo(MoveReply.Outcome.ILLEGAL);
        assertThat(reply.result()).isNull();
    }

    @Test
    void withoutAMoveTheEngineChoosesOne() throws Exception {
        MoveReply reply = ask(new MoveRequest(START, null));

        assertThat(reply.outcome()).isEqualTo(MoveReply.Outcome.PLAYED);
        assertThat(reply.result().move()).isNotNull();
        assertThat(reply.result().boardState()).contains(" b ");
    }

    @Test
    void checkmateEndsTheGame() throws Exception {
        MoveReply reply = ask(new MoveRequest("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", new Move("d1", "d8")));

        assertThat(reply.result().moveResult().get("san")).isEqualTo("Rd8#");
        assertThat(reply.result().status()).isEqualTo("CHECKMATE");
    }
}
