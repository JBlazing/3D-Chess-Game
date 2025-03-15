package com.jreynolds.game.js;

import com.jreynolds.model.Move;
import com.jreynolds.model.Result;
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
import java.util.List;
import java.util.Map;

@Component
public class JavaScriptEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaScriptEngine.class);
    private final static String allowedClasses = "com.jreynolds.game.model(.*)";

    private final String chessJs;
    private final String moveJs;
    private final String completeJs;

    private final Source chessJsSource;
    private final Context jsContext;
    public JavaScriptEngine(@Value("${game.chess.js.location}") String chessJs,
                            @Value("${game.chess.move.js.location}") String moveJs) throws IOException, URISyntaxException {
        this.chessJs = Files.readString(Paths.get(getClass().getResource(chessJs).toURI()));
        this.moveJs = Files.readString(Paths.get(getClass().getResource(moveJs).toURI()));
        this.completeJs = String.join("\n", this.chessJs, this.moveJs);
        this.chessJsSource = Source.newBuilder("js", this.completeJs, "Chess.js").build();

        this.jsContext = getContext(chessJsSource);

    }


    public Result move(Move move, String boardState)
    {
        try(Context context = getContext(chessJsSource)){
            var res = context.getBindings("js")
                    .getMember("evalMove")
                    .execute(move, boardState).as(Result.class);
            // TODO Copy History object correctly
            return new Result(res.move(),Map.copyOf(res.moveResult()), res.boardState(), Map.of());
        }catch (Exception e){
            LOGGER.error(e.getMessage());
        }
        return null;
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
