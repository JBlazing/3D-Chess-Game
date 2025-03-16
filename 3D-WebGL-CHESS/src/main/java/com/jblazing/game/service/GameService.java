package com.jblazing.game.service;


import com.jblazing.game.cache.GameStateCache;
import com.jblazing.game.js.JavaScriptEngine;
import com.jblazing.model.GameState;
import com.jblazing.model.Move;
import com.jblazing.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GameService {
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    private final GameStateCache gameStateCache;
    private final JavaScriptEngine javaScriptEngine;

    public GameService(GameStateCache gameStateCache, JavaScriptEngine javaScriptEngine) {
        this.gameStateCache = gameStateCache;
        this.javaScriptEngine = javaScriptEngine;
    }

    public void createNewGame(UUID gameId, List<String> playerIds, String currentSide) {
        gameStateCache.storeGameState(gameId, new GameState(gameId,playerIds,currentSide,null,new ArrayList<>()));
    }

    public Result move(UUID gameId, Move move){
        GameState gameState = gameStateCache.getGameState(gameId);
        Optional<Result> resultOpt = javaScriptEngine.move(move, gameState != null ? gameState.boardState() : null);
        resultOpt.ifPresent(result -> {
            gameState.gameHistory().add(result.moveHistory());
            String playersTurn = gameState.playersId().stream().filter(p -> !p.equalsIgnoreCase(gameState.currentPlayersTurn())).findFirst().get();
            GameState newState = new GameState(gameId, gameState.playersId(), playersTurn, result.boardState(), gameState.gameHistory());
            gameStateCache.storeGameState(gameId, newState);
        });
        return resultOpt.orElse(null);
    }


}
