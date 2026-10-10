package com.jblazing.game.service;


import com.jblazing.game.cache.GameStateCache;
import com.jblazing.game.js.JavaScriptEngine;
import com.jblazing.game.persistence.GameRecord;
import com.jblazing.game.persistence.GameRecordRepository;
import com.jblazing.model.GameState;
import com.jblazing.model.Move;
import com.jblazing.model.Result;
import com.jblazing.player.Player;
import com.jblazing.user.UserAccount;
import com.jblazing.user.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

/**
 * Plays games for their players. The in-memory cache keeps active games fast; every move is also
 * written to the database, which is where a game is reloaded from once it has left the cache.
 */
@Service
public class GameService {
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    private final GameStateCache gameStateCache;
    private final JavaScriptEngine javaScriptEngine;
    private final GameRecordRepository gameRecordRepository;
    private final UserAccountRepository userAccountRepository;
    // moves on the same game are applied one at a time; striped so unrelated games don't wait on each other
    private final Object[] locks = new Object[64];

    public GameService(GameStateCache gameStateCache, JavaScriptEngine javaScriptEngine,
                       GameRecordRepository gameRecordRepository, UserAccountRepository userAccountRepository) {
        this.gameStateCache = gameStateCache;
        this.javaScriptEngine = javaScriptEngine;
        this.gameRecordRepository = gameRecordRepository;
        this.userAccountRepository = userAccountRepository;
        Arrays.setAll(locks, i -> new Object());
    }

    public UUID createNewGame(Player white, Player black) {
        UUID gameId = UUID.randomUUID();
        GameState state = new GameState(gameId, white, black, GameState.STARTING_POSITION, GameState.IN_PROGRESS);
        gameRecordRepository.save(new GameRecord(gameId.toString(), white.id(), black.id(),
                account(white), account(black), state.status(), state.boardState(), Instant.now()));
        gameStateCache.storeGameState(gameId, state);
        logger.info("Created game {} white={} black={}", gameId, white.id(), black.id());
        return gameId;
    }

    /** Plays the player's move; they must be in the game and it must be their turn. */
    public Result playerMove(UUID gameId, Player player, Move move) {
        synchronized (lock(gameId)) {
            GameState state = gameFor(gameId, player);
            requireInProgress(state);
            if (!state.playerToMove().id().equals(player.id())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "It is not your turn");
            }
            Result result = javaScriptEngine.move(move, state.boardState())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Illegal move"));
            return apply(state, result);
        }
    }

    /** Lets the computer make its move in a game the player is in, when it is the computer's turn. */
    public Result computerMove(UUID gameId, Player player) {
        synchronized (lock(gameId)) {
            GameState state = gameFor(gameId, player);
            requireInProgress(state);
            if (!state.playerToMove().isComputer()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "It is not the computer's turn");
            }
            Result result = javaScriptEngine.move(null, state.boardState())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The computer could not move"));
            return apply(state, result);
        }
    }

    @Transactional(readOnly = true)
    public List<GameSummary> gamesFor(Player player) {
        List<GameRecord> records = player.accountId() != null
                ? gameRecordRepository.findRecentForAccount(player.accountId())
                : gameRecordRepository.findRecentForPlayer(player.id());
        return records.stream()
                .map(record -> GameSummary.of(record, player))
                .toList();
    }

    private Result apply(GameState state, Result result) {
        GameState next = new GameState(state.gameId(), state.white(), state.black(), result.boardState(), result.status());
        GameRecord record = gameRecordRepository.findById(state.gameId().toString()).orElseThrow();
        record.setFen(next.boardState());
        record.setStatus(next.status());
        if ("CHECKMATE".equals(next.status())) {
            // the side that just moved delivered mate
            record.setWinner("w".equals(result.moveResult().get("color")) ? "WHITE" : "BLACK");
        }
        Object san = result.moveResult().get("san");
        record.setMoves(record.getMoves().isEmpty() ? String.valueOf(san) : record.getMoves() + " " + san);
        record.setUpdatedAt(Instant.now());
        gameRecordRepository.save(record);
        gameStateCache.storeGameState(state.gameId(), next);
        return result;
    }

    /** The game, from the cache or else the database, provided the player is in it. */
    private GameState gameFor(UUID gameId, Player player) {
        GameState state = gameStateCache.getGameState(gameId);
        if (state == null) {
            state = gameRecordRepository.findById(gameId.toString()).map(GameService::toState).orElse(null);
            if (state != null) {
                gameStateCache.storeGameState(gameId, state);
            }
        }
        // someone else's game looks the same as no game, so ids can't be probed
        if (state == null || !state.hasPlayer(player)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such game");
        }
        return state;
    }

    private static GameState toState(GameRecord record) {
        return new GameState(UUID.fromString(record.getId()),
                Player.of(record.getWhitePlayer(), record.getWhiteAccount() == null ? null : record.getWhiteAccount().getId()),
                Player.of(record.getBlackPlayer(), record.getBlackAccount() == null ? null : record.getBlackAccount().getId()),
                record.getFen(), record.getStatus());
    }

    private static void requireInProgress(GameState state) {
        if (state.isOver()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The game is over");
        }
    }

    private UserAccount account(Player player) {
        return player.accountId() == null ? null : userAccountRepository.getReferenceById(player.accountId());
    }

    private Object lock(UUID gameId) {
        return locks[Math.floorMod(gameId.hashCode(), locks.length)];
    }
}
