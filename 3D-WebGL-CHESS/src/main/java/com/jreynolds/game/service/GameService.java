package com.jreynolds.game.service;


import com.jreynolds.game.cache.GameStateCache;
import com.jreynolds.game.js.JavaScriptEngine;
import com.jreynolds.model.Move;
import com.jreynolds.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class GameService {
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
    private GameStateCache gameStateCache;
    private JavaScriptEngine javaScriptEngine;

    public GameService(GameStateCache gameStateCache, JavaScriptEngine javaScriptEngine) {
        this.gameStateCache = gameStateCache;
        this.javaScriptEngine = javaScriptEngine;
    }

    public Result move(UUID gameId, Move move){
        String gameState = gameStateCache.getGameState(gameId);
        Optional<Result> resultOpt = javaScriptEngine.move(move, gameState);
        resultOpt.ifPresent(result -> gameStateCache.storeGameState(gameId, result.boardState()));
        return resultOpt.orElse(null);
    }


}
