package com.jblazing.game.js;

import com.jblazing.model.Move;
import com.jblazing.model.Result;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

@Component
public class JavaScriptEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaScriptEngine.class);

    private final Source chessJsSource;
    private final ExecutorService executor;
    private final Duration executorTimeout;
    // one engine for every move: contexts created on it share the parsed Chess.js instead of
    // parsing it again each time, which in interpreter-only mode could take seconds under load
    private final Engine engine = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();

    /** The engine failed or timed out; nothing is wrong with the move, so the player can try again. */
    @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "The chess engine is busy, try again")
    public static class EngineUnavailableException extends RuntimeException {
        EngineUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public JavaScriptEngine(@Value("${game.chess.js.location}") String chessJs,
                            @Value("${game.chess.move.js.location:}") String moveJs,
                            @Value("${game.chess.engine.executor.timeout:PT10S}") Duration executorTimeout) throws IOException, URISyntaxException {

        chessJs = Files.readString(Paths.get(getClass().getResource(chessJs).toURI()));
        moveJs = Files.readString(Paths.get(getClass().getResource(moveJs).toURI()));

        String completeJs = String.join("\n", chessJs, moveJs);

        this.chessJsSource = Source.newBuilder("js", completeJs, "Chess.js").build();
        // platform threads: GraalJS (25) only supports polyglot contexts on virtual threads experimentally
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.executorTimeout = executorTimeout;
    }

    /**
     * Plays the move (or, with a null move, a computer move) on the position.
     *
     * @return the result, or empty when the move is illegal
     * @throws EngineUnavailableException when the engine fails or doesn't answer in time
     */
    public Optional<Result> move(Move move, String boardState)
    {
        Future<Optional<Result>> submit = executor.submit(() -> {
            try (Context context = getContext(chessJsSource)) {
                var res = context.getBindings("js")
                        .getMember("evalMove")
                        .execute(move, boardState);

                if(res.isNull()){
                    return Optional.<Result>empty();   // chess.js rejected the move
                }
                Result result = res.as(Result.class);
                // TODO Copy History object correctly
                return Optional.of(new Result(result.move(), Map.copyOf(result.moveResult()), result.boardState(), Map.of(), result.status()));
            }
        });
        try {
            return submit.get(executorTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            submit.cancel(true);
            LOGGER.error("Move was not completed in time ({})", executorTimeout);
            throw new EngineUnavailableException("Move was not completed in time", e);
        } catch (ExecutionException e) {
            LOGGER.error("The chess engine failed", e.getCause());
            throw new EngineUnavailableException("The chess engine failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineUnavailableException("Interrupted", e);
        }
    }

    private Context getContext(Source source) {

        Context context = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true)
                .build();
        context.eval(source);
        context.getBindings("js").putMember("log", LOGGER);
        return context;



    }

}
