package com.jblazing.game.js;

import com.jblazing.model.Move;
import com.jblazing.model.Result;
import org.graalvm.polyglot.Context;
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
    public JavaScriptEngine(@Value("${game.chess.js.location}") String chessJs,
                            @Value("${game.chess.move.js.location}") String moveJs,
                            @Value("${game.chess.engine.executor.timeout:PT5S}") Duration executorTimeout) throws IOException, URISyntaxException {

        chessJs = Files.readString(Paths.get(getClass().getResource(chessJs).toURI()));
        moveJs = Files.readString(Paths.get(getClass().getResource(moveJs).toURI()));

        String completeJs = String.join("\n", chessJs, moveJs);

        this.chessJsSource = Source.newBuilder("js", completeJs, "Chess.js").build();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.executorTimeout = executorTimeout;
    }


    public Optional<Result> move(Move move, String boardState)
    {
        Future<Optional<Result>> submit = executor.submit(() -> {
            Optional<Result> resultOpt = Optional.empty();
            try (Context context = getContext(chessJsSource)) {
                var res = context.getBindings("js")
                        .getMember("evalMove")
                        .execute(move, boardState);

                if(!res.isNull()){
                    Result result = res.as(Result.class);
                    // TODO Copy History object correctly
                    resultOpt = Optional.of(new Result(result.move(), Map.copyOf(result.moveResult()), result.boardState(), Map.of()));
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
            }
            return resultOpt;
        });
        Optional<Result> result = Optional.empty();
        try {
            result = submit.get(executorTimeout.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            LOGGER.error("Move was not completed in time {}", e.getMessage());
            submit.cancel(true);
        }
        return result;
    }

    private Context getContext(Source source) {

        Context context = Context.newBuilder()
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true)
                .build();
        context.eval(source);
        context.getBindings("js").putMember("log", LOGGER);
        return context;



    }

}
