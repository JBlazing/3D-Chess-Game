package com.jblazing.game.service;


import com.jblazing.game.cache.GameStateCache;
import com.jblazing.game.js.JavaScriptEngine;
import com.jblazing.game.persistence.GameRecord;
import com.jblazing.game.persistence.GameRecordRepository;
import com.jblazing.model.GameClock;
import com.jblazing.model.GameState;
import com.jblazing.model.Move;
import com.jblazing.model.Result;
import com.jblazing.model.TimeControl;
import com.jblazing.player.Player;
import com.jblazing.rating.RatingService;
import com.jblazing.user.UserAccount;
import com.jblazing.user.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
 *
 * <p>Games are played on a blitz clock ({@code game.clock.*}) that only the server keeps: each
 * move spends the mover's thinking time and adds the increment, and a side that runs out of time
 * loses ({@code TIMEOUT}), or draws if the opponent couldn't have checkmated. That includes the
 * computer, whose clock runs from the end of the player's move until its own reply, which the
 * server plays after a thinking delay.
 * Every change is published as a {@link GameChanged} event.
 */
@Service
public class GameService {
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    /** How long a player may leave an untimed online game on their move before the opponent can claim the win. */
    public static final Duration ABANDON_AFTER = Duration.ofMinutes(2);

    public static final String TIMEOUT = "TIMEOUT";
    /** A game against the computer that was stopped because the player started another game. */
    public static final String ABORTED = "ABORTED";

    private final GameStateCache gameStateCache;
    private final JavaScriptEngine javaScriptEngine;
    private final GameRecordRepository gameRecordRepository;
    private final UserAccountRepository userAccountRepository;
    private final RatingService ratingService;
    private final ApplicationEventPublisher events;
    private final TimeControl blitz;
    // moves on the same game are applied one at a time; striped so unrelated games don't wait on each other
    private final Object[] locks = new Object[64];

    public GameService(GameStateCache gameStateCache, JavaScriptEngine javaScriptEngine,
                       GameRecordRepository gameRecordRepository, UserAccountRepository userAccountRepository,
                       RatingService ratingService, ApplicationEventPublisher events,
                       @Value("${game.clock.initial:PT5M}") Duration clockInitial,
                       @Value("${game.clock.increment:PT3S}") Duration clockIncrement) {
        this.gameStateCache = gameStateCache;
        this.javaScriptEngine = javaScriptEngine;
        this.gameRecordRepository = gameRecordRepository;
        this.userAccountRepository = userAccountRepository;
        this.ratingService = ratingService;
        this.events = events;
        this.blitz = new TimeControl(clockInitial, clockIncrement);
        Arrays.setAll(locks, i -> new Object());
    }

    /** A new game on the blitz clock, online or against the computer. */
    public UUID createNewGame(Player white, Player black) {
        return createNewGame(white, black, blitz);
    }

    /**
     * A new game on the given clock, or untimed when {@code timeControl} is null. The clock starts at
     * once, white to move. Any game either player still has going against the computer is stopped
     * ({@link #ABORTED}): a new game replaces it.
     */
    public UUID createNewGame(Player white, Player black, TimeControl timeControl) {
        abortComputerGames(white);
        abortComputerGames(black);
        UUID gameId = UUID.randomUUID();
        Instant now = Instant.now();
        GameClock clock = timeControl == null ? null : GameClock.start(timeControl, now);
        GameState state = new GameState(gameId, white, black, GameState.STARTING_POSITION, GameState.IN_PROGRESS, clock);
        UserAccount whiteAccount = account(white), blackAccount = account(black);
        GameRecord record = new GameRecord(gameId.toString(), white.id(), black.id(),
                whiteAccount, blackAccount, state.status(), state.boardState(), now);
        // rated only when both sides are signed-in players: never the computer, never guests
        record.setRated(whiteAccount != null && blackAccount != null);
        record.setWhiteRating(whiteAccount == null ? null : whiteAccount.getRating());
        record.setBlackRating(blackAccount == null ? null : blackAccount.getRating());
        if (clock != null) {
            record.setClockInitialMs(clock.initialMs());
            record.setClockIncrementMs(clock.incrementMs());
        }
        writeClock(record, clock);
        gameRecordRepository.save(record);
        gameStateCache.storeGameState(gameId, state);
        logger.info("Created {} game {} white={} black={}{}", record.isRated() ? "rated" : "unrated", gameId, white.id(), black.id(),
                clock == null ? "" : " clock " + TimeControl.label(clock.initialMs(), clock.incrementMs()));
        events.publishEvent(new GameChanged(gameId, state));
        return gameId;
    }

    /** Plays the player's move; they must be in the game, it must be their turn, and they must still have time. */
    public Result playerMove(UUID gameId, Player player, Move move) {
        synchronized (lock(gameId)) {
            Instant now = Instant.now();
            GameState state = gameFor(gameId, player);
            requireInProgress(state);
            if (!state.playerToMove().id().equals(player.id())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "It is not your turn");
            }
            // the flag fell before the move arrived: the game is over, not the move played
            if (state.isTimed() && state.clock().flagged(state.whiteToMove(), now)) {
                timeOut(state, now);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Your time ran out");
            }
            Result result = javaScriptEngine.move(move, state.boardState())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Illegal move"));
            return apply(state, result, now);
        }
    }

    /**
     * The computer makes its move now, if it is the computer's turn. The server decides when: see
     * {@code ComputerOpponent}, which waits a thinking delay first; players never ask for it.
     *
     * @return the move, or empty when it isn't the computer's turn (or the game is over)
     */
    public Optional<Result> playComputerMove(UUID gameId) {
        return playComputerMove(gameId, null);
    }

    /**
     * The same, but only from the given position (FEN): a move scheduled for one position is
     * dropped if the game has moved on, ended, or been stopped meanwhile.
     */
    public Optional<Result> playComputerMove(UUID gameId, String expectedPosition) {
        synchronized (lock(gameId)) {
            GameState state = loadState(gameId);
            if (state == null || state.isOver() || !state.playerToMove().isComputer()
                    || (expectedPosition != null && !expectedPosition.equals(state.boardState()))) {
                return Optional.empty();
            }
            Instant now = Instant.now();
            if (state.isTimed() && state.clock().flagged(state.whiteToMove(), now)) {
                timeOut(state, now);
                return Optional.empty();
            }
            Result result = javaScriptEngine.move(null, state.boardState())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The computer could not move"));
            return Optional.of(apply(state, result, Instant.now()));
        }
    }

    /** The player gives up; the opponent wins. */
    public void resign(UUID gameId, Player player) {
        synchronized (lock(gameId)) {
            GameState state = gameFor(gameId, player);
            requireInProgress(state);
            finish(state, "RESIGNED", isWhite(state, player) ? "BLACK" : "WHITE", Instant.now());
        }
    }

    /**
     * Ends an untimed online game the opponent has walked away from: it has been their move for
     * longer than {@link #ABANDON_AFTER}. The player claiming wins. Timed games don't need this:
     * the absent player's clock runs out.
     */
    public void claimWin(UUID gameId, Player player) {
        synchronized (lock(gameId)) {
            GameState state = gameFor(gameId, player);
            requireInProgress(state);
            GameRecord record = gameRecordRepository.findById(gameId.toString()).orElseThrow();
            if (!canClaimWin(state, record, player)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Your opponent still has time to move");
            }
            finish(state, "ABANDONED", isWhite(state, player) ? "WHITE" : "BLACK", Instant.now());
        }
    }

    /**
     * Ends the game if the side to move has run out of time; called when its flag is due to fall.
     *
     * @return whether the game ended now
     */
    public boolean flagIfOutOfTime(UUID gameId) {
        synchronized (lock(gameId)) {
            Instant now = Instant.now();
            GameState state = loadState(gameId);
            if (state == null || state.isOver() || !state.isTimed() || !state.clock().flagged(state.whiteToMove(), now)) {
                return false;
            }
            timeOut(state, now);
            return true;
        }
    }

    /** The game from the player's side, pushed (and polled) while it is played. */
    @Transactional(readOnly = true)
    public LiveGame liveGame(UUID gameId, Player player) {
        Instant now = Instant.now();
        GameState state = gameFor(gameId, player);
        GameRecord record = gameRecordRepository.findById(gameId.toString()).orElseThrow();
        boolean white = isWhite(state, player);
        boolean opponentToMove = !state.isOver() && !state.playerToMove().id().equals(player.id());
        long idle = opponentToMove ? Duration.between(record.getUpdatedAt(), now).toSeconds() : 0;
        return new LiveGame(record.getId(), white ? "WHITE" : "BLACK",
                side(state.white(), record.getWhiteAccount(), record.getWhiteRating()),
                side(state.black(), record.getBlackAccount(), record.getBlackRating()),
                isOnline(state), record.isRated(), record.getStatus(), record.getWinner(), record.getFen(),
                record.getMoves().isEmpty() ? List.of() : List.of(record.getMoves().split(" ")),
                Math.max(0, idle), canClaimWin(state, record, player),
                white ? record.getWhiteRatingChange() : record.getBlackRatingChange(),
                clockView(state, now));
    }

    /**
     * The clock of a game as it reads right now, for pushing to the players every second; empty for
     * untimed and unknown games. Not access-checked: only for callers that have already checked the player.
     */
    public Optional<LiveGame.Clock> clockNow(UUID gameId) {
        return Optional.ofNullable(loadState(gameId)).map(state -> clockView(state, Instant.now()));
    }

    /** The game as it stands, whoever's it is; for the server's own bookkeeping, not for players. */
    public Optional<GameState> stateOf(UUID gameId) {
        return Optional.ofNullable(loadState(gameId));
    }

    /** The games being played on a clock right now. */
    public List<UUID> timedGamesInProgress() {
        return gameRecordRepository.findTimedInProgress().stream().map(UUID::fromString).toList();
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

    /** Records a move made at {@code now}: the mover's clock is charged up to then, and the opponent's starts. */
    private Result apply(GameState state, Result result, Instant now) {
        GameClock clock = state.isTimed() ? state.clock().afterMove(state.whiteToMove(), now) : null;
        GameState next = new GameState(state.gameId(), state.white(), state.black(), result.boardState(), result.status(), clock);
        GameRecord record = gameRecordRepository.findById(state.gameId().toString()).orElseThrow();
        record.setFen(next.boardState());
        record.setStatus(next.status());
        if ("CHECKMATE".equals(next.status())) {
            // the side that just moved delivered mate
            record.setWinner("w".equals(result.moveResult().get("color")) ? "WHITE" : "BLACK");
        }
        Object san = result.moveResult().get("san");
        record.setMoves(record.getMoves().isEmpty() ? String.valueOf(san) : record.getMoves() + " " + san);
        record.setUpdatedAt(now);
        writeClock(record, clock);
        gameRecordRepository.save(record);
        gameStateCache.storeGameState(state.gameId(), next);
        if (next.isOver()) {
            ratingService.applyResult(record.getId());
        }
        events.publishEvent(new GameChanged(state.gameId(), next));
        return result;
    }

    /** Ends the game without a move (resignation, abandonment, timeout), stops its clock and rates it. */
    private void finish(GameState state, String status, String winner, Instant now) {
        GameClock clock = state.isTimed() ? state.clock().stopped(state.whiteToMove(), now) : null;
        GameState next = new GameState(state.gameId(), state.white(), state.black(), state.boardState(), status, clock);
        GameRecord record = gameRecordRepository.findById(state.gameId().toString()).orElseThrow();
        record.setStatus(status);
        record.setWinner(winner);
        record.setUpdatedAt(now);
        writeClock(record, clock);
        gameRecordRepository.save(record);
        gameStateCache.storeGameState(state.gameId(), next);
        logger.info("Game {} ended: {} (winner {})", state.gameId(), status, winner);
        ratingService.applyResult(record.getId());
        events.publishEvent(new GameChanged(state.gameId(), next));
    }

    /** Stops the player's unfinished games against the computer: no winner, the clock stopped, nothing rated. */
    private void abortComputerGames(Player player) {
        if (player.isComputer()) {
            return;
        }
        List<GameRecord> recent = player.accountId() != null
                ? gameRecordRepository.findRecentForAccount(player.accountId())
                : gameRecordRepository.findRecentForPlayer(player.id());
        recent.stream()
                .filter(record -> GameState.IN_PROGRESS.equals(record.getStatus()))
                .filter(record -> Player.COMPUTER.id().equals(record.getWhitePlayer()) || Player.COMPUTER.id().equals(record.getBlackPlayer()))
                .map(record -> UUID.fromString(record.getId()))
                .forEach(gameId -> {
                    synchronized (lock(gameId)) {
                        GameState state = loadState(gameId);
                        if (state != null && !state.isOver()) {
                            finish(state, ABORTED, null, Instant.now());
                        }
                    }
                });
    }

    /** The side to move ran out of time. */
    private void timeOut(GameState state, Instant now) {
        finish(state, TIMEOUT, timeoutWinner(state.boardState()), now);
    }

    /**
     * Who wins when the side to move runs out of time: the opponent, unless the opponent has too
     * little left to ever checkmate (a lone king, or king and one knight or bishop against a lone
     * king), which makes it a draw (null).
     */
    static String timeoutWinner(String fen) {
        String[] fields = fen.split(" ");
        boolean whiteFlagged = fields[1].equals("w");
        String winnerPieces = pieces(fields[0], !whiteFlagged), loserPieces = pieces(fields[0], whiteFlagged);
        boolean cannotMate = winnerPieces.isEmpty()
                || (winnerPieces.length() == 1 && "nb".contains(winnerPieces) && loserPieces.isEmpty());
        return cannotMate ? null : whiteFlagged ? "BLACK" : "WHITE";
    }

    /** One side's pieces other than the king, as lowercase letters. */
    private static String pieces(String board, boolean white) {
        StringBuilder pieces = new StringBuilder();
        for (char c : board.toCharArray()) {
            if (Character.isLetter(c) && Character.isUpperCase(c) == white && Character.toLowerCase(c) != 'k') {
                pieces.append(Character.toLowerCase(c));
            }
        }
        return pieces.toString();
    }

    private static void writeClock(GameRecord record, GameClock clock) {
        record.setWhiteClockMs(clock == null ? null : clock.whiteMs());
        record.setBlackClockMs(clock == null ? null : clock.blackMs());
    }

    private static LiveGame.Clock clockView(GameState state, Instant now) {
        if (!state.isTimed()) {
            return null;
        }
        GameClock clock = state.clock();
        boolean whiteToMove = state.whiteToMove();
        boolean running = !state.isOver();
        return new LiveGame.Clock(TimeControl.label(clock.initialMs(), clock.incrementMs()),
                running ? clock.remainingMs(true, whiteToMove, now) : clock.whiteMs(),
                running ? clock.remainingMs(false, whiteToMove, now) : clock.blackMs(),
                running ? (whiteToMove ? "WHITE" : "BLACK") : null);
    }

    private boolean canClaimWin(GameState state, GameRecord record, Player player) {
        return isOnline(state) && !state.isTimed() && !state.isOver()
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
                : account != null ? account.displayName()
                : "Guest";
        return new LiveGame.Side(name, rating);
    }

    /** The game, from the cache or else the database, provided the player is in it. */
    private GameState gameFor(UUID gameId, Player player) {
        GameState state = loadState(gameId);
        // someone else's game looks the same as no game, so ids can't be probed
        if (state == null || !state.hasPlayer(player)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such game");
        }
        return state;
    }

    /** The game from the cache or else the database; null if there is no such game. */
    private GameState loadState(UUID gameId) {
        GameState state = gameStateCache.getGameState(gameId);
        if (state == null) {
            state = gameRecordRepository.findById(gameId.toString()).map(this::toState).orElse(null);
            if (state != null) {
                gameStateCache.storeGameState(gameId, state);
            }
        }
        return state;
    }

    private GameState toState(GameRecord record) {
        GameClock clock = record.getClockInitialMs() == null ? null
                : new GameClock(record.getClockInitialMs(), record.getClockIncrementMs(),
                        record.getWhiteClockMs(), record.getBlackClockMs(), record.getUpdatedAt());
        return new GameState(UUID.fromString(record.getId()),
                Player.of(record.getWhitePlayer(), record.getWhiteAccount() == null ? null : record.getWhiteAccount().getId()),
                Player.of(record.getBlackPlayer(), record.getBlackAccount() == null ? null : record.getBlackAccount().getId()),
                record.getFen(), record.getStatus(), clock);
    }

    private void requireInProgress(GameState state) {
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
