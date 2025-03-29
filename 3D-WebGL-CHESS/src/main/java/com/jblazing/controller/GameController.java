package com.jblazing.controller;


import com.jblazing.game.cache.GameStateCache;
import com.jblazing.game.service.GameService;
import com.jblazing.model.GameStart;
import com.jblazing.model.Move;
import com.jblazing.model.Result;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/game")
@AllArgsConstructor
public class GameController {

    private static final String BLACK = "black";
    private static final String COMPUTER = "COMPUTER";

    GameService gameService;
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
