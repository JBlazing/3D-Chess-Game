package com.jreynolds.game.service;


import com.jreynolds.game.cache.GameStateCache;
import com.jreynolds.game.js.JavaScriptEngine;
import com.jreynolds.model.Move;
import com.jreynolds.model.Result;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class GameService {

    private GameStateCache gameStateCache;
    private JavaScriptEngine javaScriptEngine;

    public GameService(GameStateCache gameStateCache, JavaScriptEngine javaScriptEngine) {
        this.gameStateCache = gameStateCache;
        this.javaScriptEngine = javaScriptEngine;
    }

    public Map<String,String> move(UUID gameId, Move move){
        String gameState = gameStateCache.getGameState(gameId);
        Result result = javaScriptEngine.move(move, gameState);
        gameStateCache.storeGameState(gameId, result.boardState());
        return result.moveResult();
    }


}
