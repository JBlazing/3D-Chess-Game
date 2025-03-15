package com.jreynolds.game.js;

import com.jreynolds.model.Move;
import com.jreynolds.model.Result;
import org.graalvm.polyglot.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Component
public class JavaScriptEngine {

    private final static String allowedClasses = "com.jreynolds.game.model(.*)";

    private final String chessJs;
    private final String moveJs;

    public JavaScriptEngine(@Value("${game.chess.js.location}") String chessJs,
                            @Value("${game.chess.move.js.location}") String moveJs) throws IOException, URISyntaxException {
        this.chessJs = Files.readString(Paths.get(getClass().getResource(chessJs).toURI()));
        this.moveJs = "(" + Files.readString(Paths.get(getClass().getResource(moveJs).toURI())) + ")";
    }


    public Result move(Move move, String boardState)
    {
        try(Context context = getContext()){

            org.graalvm.polyglot.Value compiled = context.eval("js", moveJs);
            return compiled.execute(move, boardState).as(Result.class);
        }
    }



    private Context getContext() {

        return Context.newBuilder()
                .allowHostClassLookup(className -> className.matches(allowedClasses) )
                .allowExperimentalOptions(true)
                .option("js.nashorn-compat", "true")
                .build();


    }

}
