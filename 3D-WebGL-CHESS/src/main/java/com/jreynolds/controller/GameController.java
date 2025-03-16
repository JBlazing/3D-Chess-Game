package com.jreynolds.controller;


import com.jreynolds.game.cache.GameStateCache;
import com.jreynolds.game.service.GameService;
import com.jreynolds.model.GameStart;
import com.jreynolds.model.GameState;
import com.jreynolds.model.Move;
import com.jreynolds.model.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/game")
public class GameController {

    private static final String BLACK = "black";
    private static final String COMPUTER = "COMPUTER";

    @Autowired
    GameService gameService;
    @Autowired
    GameStateCache gameStateCache;
    @GetMapping("start")
    public GameStart startGame(@RequestParam String startingSide) {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Move startingMove = null;
        String currentSide = BLACK.equalsIgnoreCase(startingSide) ? COMPUTER : playerId.toString();
        gameService.createNewGame(gameId,List.of(COMPUTER, playerId.toString()), currentSide);
        if(BLACK.equalsIgnoreCase(startingSide)) {
           Result result = gameService.move(gameId,null);
           startingMove = result.move();
        }
       return new GameStart(gameId, playerId , COMPUTER, startingMove);
    }

    @PostMapping("{gameId}/move/{playerId}")
    public Result move(@PathVariable("gameId") UUID gameId,
                       @PathVariable("playerId") UUID playerId,
                       @RequestBody Move move){
        return gameService.move(gameId,move);
    }

    @GetMapping("{gameId}/opponents/move")
    public Result move(@PathVariable("gameId") UUID gameId){
        return gameService.move(gameId,null);
    }


}
