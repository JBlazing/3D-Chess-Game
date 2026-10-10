package com.jblazing.controller;


import com.jblazing.game.service.GameService;
import com.jblazing.game.service.GameSummary;
import com.jblazing.model.GameStart;
import com.jblazing.model.Move;
import com.jblazing.model.Result;
import com.jblazing.player.Player;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Games against the computer, owned by the signed-in player (see {@link Player}). */
@RestController
@RequestMapping(path = "/game")
@AllArgsConstructor
public class GameController {

    private static final String BLACK = "black";

    GameService gameService;

    @GetMapping("start")
    public GameStart startGame(@RequestParam String startingSide, Player player) {
        boolean playerIsBlack = BLACK.equalsIgnoreCase(startingSide);
        UUID gameId = playerIsBlack
                ? gameService.createNewGame(Player.COMPUTER, player)
                : gameService.createNewGame(player, Player.COMPUTER);
        Move startingMove = null;
        if (playerIsBlack) {
            startingMove = gameService.computerMove(gameId, player).move();
        }
        return new GameStart(gameId, player.id(), Player.COMPUTER.id(), startingMove);
    }

    @PostMapping("{gameId}/move")
    public Result move(@PathVariable("gameId") UUID gameId, @RequestBody Move move, Player player) {
        return gameService.playerMove(gameId, player, move);
    }

    @GetMapping("{gameId}/opponents/move")
    public Result opponentMove(@PathVariable("gameId") UUID gameId, Player player) {
        return gameService.computerMove(gameId, player);
    }

    /** The signed-in player's recent games, newest first. */
    @GetMapping("mine")
    public List<GameSummary> myGames(Player player) {
        return gameService.gamesFor(player);
    }

    /** One of the signed-in player's games, with its moves, for replaying. */
    @GetMapping("{gameId}")
    public GameSummary game(@PathVariable("gameId") UUID gameId, Player player) {
        return gameService.summaryFor(gameId, player);
    }
}
