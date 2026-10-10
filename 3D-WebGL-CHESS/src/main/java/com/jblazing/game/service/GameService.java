package com.jblazing.game.service;


import com.jblazing.game.cache.GameStateCache;
import com.jblazing.game.js.JavaScriptEngine;
import com.jblazing.game.persistence.GameRecord;
import com.jblazing.game.persistence.GameRecordRepository;
import com.jblazing.model.GameState;
import com.jblazing.model.Move;
import com.jblazing.model.Result;
import com.jblazing.player.Player;
import com.jblazing.rating.RatingService;
import com.jblazing.user.UserAccount;
import com.jblazing.user.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Plays games for their players. The in-memory cache keeps active games fast; every move is also
 * written to the database, which is where a game is reloaded from once it has left the cache.
 *
 * <p>Games between two signed-in players are rated: whenever one ends, however it ends, the result
 * goes to the {@link RatingService}. Games against the computer never change ratings.
 */
@Service
public class GameService {
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    /** How long a player may leave an online game on their move before the opponent can claim the win. */
    public static final Duration ABANDON_AFTER = Duration.ofMinutes(2);

    private final GameStateCache gameStateCache;
    private final JavaScriptEngine javaScriptEngine;
    private final GameRecordRepository gameRecordRepository;
    private final UserAccountRepository userAccountRepository;
    private final RatingService ratingService;
    // moves on the same game are applied one at a time; striped so unrelated games don't wait on each other
    private final Object[] locks = new Object[64];

    public GameService(GameStateCache gameStateCache, JavaScriptEngine javaScriptEngine,
                       GameRecordRepository gameRecordRepository, UserAccountRepository userAccountRepository,
                       RatingService ratingService) {
        this.gameStateCache = gameStateCache;
        this.javaScriptEngine = javaScriptEngine;
        this.gameRecordRepository = gameRecordRepository;
        this.userAccountRepository = userAccountRepository;
        this.ratingService = ratingService;
        Arrays.setAll(locks, i -> new Object());
    }

    public UUID createNewGame(Player white, Player black) {
        UUID gameId = UUID.randomUUID();
        GameState state = new GameState(gameId, white, black, GameState.STARTING_POSITION, GameState.IN_PROGRESS);
        UserAccount whiteAccount = account(white), blackAccount = account(black);
        GameRecord record = new GameRecord(gameId.toString(), white.id(), black.id(),
                whiteAccount, blackAccount, state.status(), state.boardState(), Instant.now());
        // rated only when both sides are signed-in players: never the computer, never guests
        record.setRated(whiteAccount != null && blackAccount != null);
        record.setWhiteRating(whiteAccount == null ? null : whiteAccount.getRating());
        record.setBlackRating(blackAccount == null ? null : blackAccount.getRating());
        gameRecordRepository.save(record);
        gameStateCache.storeGameState(gameId, state);
        logger.info("Created {} game {} white={} black={}", record.isRated() ? "rated" : "unrated", gameId, white.id(), black.id());
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

    /** The player gives up; the opponent wins. */
    public void resign(UUID gameId, Player player) {
        synchronized (lock(gameId)) {
            GameState state = gameFor(gameId, player);
            requireInProgress(state);
            finish(state, "RESIGNED", isWhite(state, player) ? "BLACK" : "WHITE");
        }
    }

    /**
     * Ends an online game the opponent has walked away from: it has been their move for longer
     * than {@link #ABANDON_AFTER}. The player claiming wins.
     */
    public void claimWin(UUID gameId, Player player) {
        synchronized (lock(gameId)) {
            GameState state = gameFor(gameId, player);
            requireInProgress(state);
            GameRecord record = gameRecordRepository.findById(gameId.toString()).orElseThrow();
            if (!canClaimWin(state, record, player)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Your opponent still has time to move");
            }
            finish(state, "ABANDONED", isWhite(state, player) ? "WHITE" : "BLACK");
        }
    }

    /** The game from the player's side, for polling while it is played. */
    @Transactional(readOnly = true)
    public LiveGame liveGame(UUID gameId, Player player) {
        GameState state = gameFor(gameId, player);
        GameRecord record = gameRecordRepository.findById(gameId.toString()).orElseThrow();
        boolean white = isWhite(state, player);
        boolean opponentToMove = !state.isOver() && !state.playerToMove().id().equals(player.id());
        long idle = opponentToMove ? Duration.between(record.getUpdatedAt(), Instant.now()).toSeconds() : 0;
        return new LiveGame(record.getId(), white ? "WHITE" : "BLACK",
                side(state.white(), record.getWhiteAccount(), record.getWhiteRating()),
                side(state.black(), record.getBlackAccount(), record.getBlackRating()),
                isOnline(state), record.isRated(), record.getStatus(), record.getWinner(), record.getFen(),
                record.getMoves().isEmpty() ? List.of() : List.of(record.getMoves().split(" ")),
                Math.max(0, idle), canClaimWin(state, record, player),
                white ? record.getWhiteRatingChange() : record.getBlackRatingChange());
    }

    /** The player's online game that is still being played, if any, e.g. to pick it up again after a reload. */
    @Transactional(readOnly = true)
    public Optional<LiveGame> activeOnlineGame(Player player) {
        return (player.accountId() != null
                ? gameRecordRepository.findRecentForAccount(player.accountId())
                : gameRecordRepository.findRecentForPlayer(player.id())).stream()
                .filter(record -> GameState.IN_PROGRESS.equals(record.getStatus()))
                .filter(record -> !Player.COMPUTER.id().equals(record.getWhitePlayer()) && !Player.COMPUTER.id().equals(record.getBlackPlayer()))
                .findFirst()
                .map(record -> liveGame(UUID.fromString(record.getId()), player));
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

    /** One of the player's games, e.g. to replay it. */
    @Transactional(readOnly = true)
    public GameSummary summaryFor(UUID gameId, Player player) {
        return gameRecordRepository.findById(gameId.toString())
                .filter(record -> record.getWhitePlayer().equals(player.id()) || record.getBlackPlayer().equals(player.id()))
                .map(record -> GameSummary.of(record, player))
                // someone else's game looks the same as no game
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such game"));
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
        if (next.isOver()) {
            ratingService.applyResult(record.getId());
        }
        return result;
    }

    /** Ends the game without a move (resignation, abandonment) and rates it. */
    private void finish(GameState state, String status, String winner) {
        GameRecord record = gameRecordRepository.findById(state.gameId().toString()).orElseThrow();
        record.setStatus(status);
        record.setWinner(winner);
        record.setUpdatedAt(Instant.now());
        gameRecordRepository.save(record);
        gameStateCache.storeGameState(state.gameId(), new GameState(state.gameId(), state.white(), state.black(), state.boardState(), status));
        logger.info("Game {} ended: {} (winner {})", state.gameId(), status, winner);
        ratingService.applyResult(record.getId());
    }

    private boolean canClaimWin(GameState state, GameRecord record, Player player) {
        return isOnline(state) && !state.isOver()
                && !state.playerToMove().id().equals(player.id())
                && Duration.between(record.getUpdatedAt(), Instant.now()).compareTo(ABANDON_AFTER) >= 0;
    }

    private static boolean isOnline(GameState state) {
        return !state.white().isComputer() && !state.black().isComputer();
    }

    private static boolean isWhite(GameState state, Player player) {
        return state.white().id().equals(player.id());
    }

    private static LiveGame.Side side(Player player, UserAccount account, Integer rating) {
        String name = player.isComputer() ? "Computer"
                : account != null ? Objects.requireNonNullElse(account.getName(), "Player")
                : "Guest";
        return new LiveGame.Side(name, rating);
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
        return player.accountId() == null ? null : userAccountRepository.findById(player.accountId()).orElse(null);
    }

    private Object lock(UUID gameId) {
        return locks[Math.floorMod(gameId.hashCode(), locks.length)];
    }
}
