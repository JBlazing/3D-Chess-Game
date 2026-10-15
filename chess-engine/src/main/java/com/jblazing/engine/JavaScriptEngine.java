package com.jblazing.engine;

import com.jblazing.model.Move;
import com.jblazing.model.Result;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/** Chess.js (the rules, from chess-engine-api) and Move.js (playing a move with them) on GraalJS. */
@Component
public class JavaScriptEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaScriptEngine.class);
    private static final String START_POSITION = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final int WARM_UP_MOVES = 5;

    private final Source chessJsSource;
    // one engine for every move: contexts created on it share the parsed Chess.js instead of
    // parsing it again each time, which in interpreter-only mode could take seconds under load
    private final Engine engine = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();

    public JavaScriptEngine(@Value("${chess.engine.chess-js:/static/js/Chess.js}") String chessJs,
                            @Value("${chess.engine.move-js:/Move.js}") String moveJs) {
        String completeJs = String.join("\n", read(chessJs), read(moveJs));
        this.chessJsSource = Source.newBuilder("js", completeJs, "Chess.js").buildLiteral();
    }

    /**
     * Plays the move (or, with a null move, a computer move) on the position. Safe to call from
     * several threads at once: each move gets its own context.
     *
     * @return the result, or empty when the move is illegal
     */
    public Optional<Result> move(Move move, String boardState) {
        try (Context context = newContext()) {
            var res = context.getBindings("js")
                    .getMember("evalMove")
                    .execute(move, boardState);

            if (res.isNull()) {
                return Optional.empty();   // chess.js rejected the move
            }
            Result result = res.as(Result.class);
            // TODO Copy History object correctly
            return Optional.of(new Result(result.move(), Map.copyOf(result.moveResult()), result.boardState(), Map.of(), result.status()));
        }
    }

    /**
     * Plays a few moves once the app is up, in the background, so the first real move doesn't pay
     * for parsing Chess.js and running it uncompiled (most of a second on a cold engine).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread.ofPlatform().name("chess-engine-warm-up").daemon().start(() -> {
            long started = System.nanoTime();
            try {
                for (int i = 0; i < WARM_UP_MOVES; i++) {
                    move(new Move("e2", "e4"), START_POSITION);
                    move(null, START_POSITION);
                }
                // "Interpreted" means no JIT compiler (e.g. not on a GraalVM JDK): every move runs far slower
                LOGGER.info("Chess engine warmed up in {} ms on the {} runtime",
                        (System.nanoTime() - started) / 1_000_000, engine.getImplementationName());
            } catch (RuntimeException e) {
                // only the first real move is slower for it
                LOGGER.warn("Chess engine warm-up failed", e);
            }
        });
    }

    private Context newContext() {
        Context context = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true)
                .build();
        context.eval(chessJsSource);
        return context;
    }

    /** A script from the classpath; a stream, since it may be inside a jar. */
    private static String read(String resource) {
        try (InputStream in = JavaScriptEngine.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No " + resource + " on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        }
    }
}
