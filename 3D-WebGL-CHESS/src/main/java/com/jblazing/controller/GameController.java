package com.jblazing.controller;


import com.jblazing.game.events.GameEventStream;
import com.jblazing.game.service.GameService;
import com.jblazing.game.service.GameSummary;
import com.jblazing.game.service.LiveGame;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.jblazing.model.GameStart;
import com.jblazing.model.Move;
import com.jblazing.model.Result;
import com.jblazing.player.Player;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/** Games against the computer, owned by the signed-in player (see {@link Player}). */
@RestController
@RequestMapping(path = "/game")
@AllArgsConstructor
public class GameController {

    private static final String BLACK = "black";

    GameService gameService;
    GameEventStream gameEventStream;

    @GetMapping("start")
    public GameStart startGame(@RequestParam String startingSide, Player player) {
        boolean playerIsBlack = BLACK.equalsIgnoreCase(startingSide);
        UUID gameId = playerIsBlack
                ? gameService.createNewGame(Player.COMPUTER, player)
                : gameService.createNewGame(player, Player.COMPUTER);
        // the computer's moves, the opening included, are played by the server and pushed over /events
        return new GameStart(gameId, player.id(), Player.COMPUTER.id());
    }

    @PostMapping("{gameId}/move")
    public Result move(@PathVariable("gameId") UUID gameId, @RequestBody Move move, Player player) {
        return gameService.playerMove(gameId, player, move);
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

    /** The game as it stands, e.g. when the page opens an online game. */
    @GetMapping("{gameId}/state")
    public LiveGame state(@PathVariable("gameId") UUID gameId, Player player) {
        return gameService.liveGame(gameId, player);
    }

    /**
     * The game pushed live as Server-Sent Events while it is played: "state" when it changes,
     * "clock" every second while the blitz clock runs. See {@link GameEventStream}.
     */
    @GetMapping(path = "{gameId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable("gameId") UUID gameId, Player player) {
        return gameEventStream.subscribe(gameId, player);
    }

    @PostMapping("{gameId}/resign")
    public LiveGame resign(@PathVariable("gameId") UUID gameId, Player player) {
        gameService.resign(gameId, player);
        return gameService.liveGame(gameId, player);
    }

    /** Win an online game the opponent has abandoned (gone too long without moving). */
    @PostMapping("{gameId}/claim")
    public LiveGame claimWin(@PathVariable("gameId") UUID gameId, Player player) {
        gameService.claimWin(gameId, player);
        return gameService.liveGame(gameId, player);
    }

    /** The player's online game still in progress, so a reloaded page can carry on; 204 if none. */
    @GetMapping("active")
    public ResponseEntity<LiveGame> active(Player player) {
        return gameService.activeOnlineGame(player)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
