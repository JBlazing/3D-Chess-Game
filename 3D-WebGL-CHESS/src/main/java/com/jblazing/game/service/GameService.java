package com.jblazing.game.service;


import com.jblazing.game.cache.GameStateCache;
import com.jblazing.game.engine.ChessEngineClient;
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
 * Plays games for their players. A game in progress is kept only in the {@link GameStateCache}
 * (Redis); the database gets it once, when it ends, and is where finished games are read from.
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

    /** The most games {@link #gamesFor} lists. */
    static final int RECENT_GAMES = 50;

    private final GameStateCache gameStateCache;
    private final ChessEngineClient chessEngine;
    private final GameRecordRepository gameRecordRepository;
    private final UserAccountRepository userAccountRepository;
    private final RatingService ratingService;
    private final ApplicationEventPublisher events;
    private final TimeControl blitz;
    // moves on the same game are applied one at a time; striped so unrelated games don't wait on each other
    private final Object[] locks = new Object[64];

    public GameService(GameStateCache gameStateCache, ChessEngineClient chessEngine,
                       GameRecordRepository gameRecordRepository, UserAccountRepository userAccountRepository,
                       RatingService ratingService, ApplicationEventPublisher events,
                       @Value("${game.clock.initial:PT5M}") Duration clockInitial,
                       @Value("${game.clock.increment:PT3S}") Duration clockIncrement) {
        this.gameStateCache = gameStateCache;
        this.chessEngine = chessEngine;
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
        UserAccount whiteAccount = account(white), blackAccount = account(black);
        // rated only when both sides are signed-in players: never the computer, never guests
        boolean rated = whiteAccount != null && blackAccount != null;
        GameState state = GameState.start(gameId, white, black, clock, rated,
                whiteAccount == null ? null : whiteAccount.getRating(),
                blackAccount == null ? null : blackAccount.getRating(), now);
        gameStateCache.storeGameState(gameId, state);
        logger.info("Created {} game {} white={} black={}{}", rated ? "rated" : "unrated", gameId, white.id(), black.id(),
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
            Result result = chessEngine.move(move, state.boardState())
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
            Result result = chessEngine.move(null, state.boardState())
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
            if (!canClaimWin(state, player)) {
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
    public LiveGame liveGame(UUID gameId, Player player) {
        Instant now = Instant.now();
        GameState state = gameFor(gameId, player);
        boolean white = isWhite(state, player);
        boolean opponentToMove = !state.isOver() && !state.playerToMove().id().equals(player.id());
        long idle = opponentToMove ? Duration.between(state.updatedAt(), now).toSeconds() : 0;
        // ratings change when a rated game ends, by which time it is in the database
        Integer ratingChange = state.isOver() && state.rated()
                ? gameRecordRepository.findById(gameId.toString())
                        .map(record -> white ? record.getWhiteRatingChange() : record.getBlackRatingChange())
                        .orElse(null)
                : null;
        return new LiveGame(gameId.toString(), white ? "WHITE" : "BLACK",
                side(state.white(), state.whiteRating()), side(state.black(), state.blackRating()),
                state.isOnline(), state.rated(), state.status(), state.winner(), state.boardState(), state.moves(),
                Math.max(0, idle), canClaimWin(state, player), ratingChange, clockView(state, now));
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

    /** The games being played right now, e.g. to pick up their clocks and computer moves after a restart. */
    public Set<UUID> gamesInProgress() {
        Set<UUID> games = new HashSet<>(gameStateCache.gamesInProgress());
        // timed games started before games in progress were kept only in Redis are in the database
        gameRecordRepository.findTimedInProgress().forEach(id -> games.add(UUID.fromString(id)));
        return games;
    }

    /** The player's online game that is still being played, if any, e.g. to pick it up again after a reload. */
    public Optional<LiveGame> activeOnlineGame(Player player) {
        return inProgress(player).stream()
                .filter(GameState::isOnline)
                .max(Comparator.comparing(GameState::updatedAt))
                .map(state -> liveGame(state.gameId(), player));
    }

    /** The player's most recent games, newest first: the ones in progress and the finished ones. */
    @Transactional(readOnly = true)
    public List<GameSummary> gamesFor(Player player) {
        Map<String, GameSummary> games = new HashMap<>();
        inProgress(player).forEach(state -> games.put(state.gameId().toString(), summary(state, player)));
        (player.accountId() != null
                ? gameRecordRepository.findRecentForAccount(player.accountId())
                : gameRecordRepository.findRecentForPlayer(player.id()))
                .forEach(record -> games.putIfAbsent(record.getId(), GameSummary.of(record, player)));
        return games.values().stream()
                .sorted(Comparator.comparing(GameSummary::updatedAt).reversed())
                .limit(RECENT_GAMES)
                .toList();
    }

    /** One of the player's games, e.g. to replay it. */
    @Transactional(readOnly = true)
    public GameSummary summaryFor(UUID gameId, Player player) {
        GameState state = gameStateCache.getGameState(gameId);
        if (state != null && !state.isOver()) {
            if (!state.hasPlayer(player)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such game");
            }
            return summary(state, player);
        }
        return gameRecordRepository.findById(gameId.toString())
                .filter(record -> record.getWhitePlayer().equals(player.id()) || record.getBlackPlayer().equals(player.id()))
                .map(record -> GameSummary.of(record, player))
                // someone else's game looks the same as no game
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such game"));
    }

    /** Records a move made at {@code now}: the mover's clock is charged up to then, and the opponent's starts. */
    private Result apply(GameState state, Result result, Instant now) {
        GameClock clock = state.isTimed() ? state.clock().afterMove(state.whiteToMove(), now) : null;
        // the side that just moved delivered mate
        String winner = "CHECKMATE".equals(result.status())
                ? ("w".equals(result.moveResult().get("color")) ? "WHITE" : "BLACK")
                : null;
        GameState next = state.afterMove(String.valueOf(result.moveResult().get("san")), result.boardState(),
                result.status(), winner, clock, now);
        save(next);
        events.publishEvent(new GameChanged(state.gameId(), next));
        return result;
    }

    /** Ends the game without a move (resignation, abandonment, timeout), stops its clock and rates it. */
    private void finish(GameState state, String status, String winner, Instant now) {
        GameClock clock = state.isTimed() ? state.clock().stopped(state.whiteToMove(), now) : null;
        GameState next = state.ended(status, winner, clock, now);
        save(next);
        logger.info("Game {} ended: {} (winner {})", state.gameId(), status, winner);
        events.publishEvent(new GameChanged(state.gameId(), next));
    }

    /**
     * Keeps the game as it now stands. A game that has just ended is written to the database first,
     * and rated: if that fails, the game is left as it was, still in progress.
     */
    private void save(GameState state) {
        if (state.isOver()) {
            gameRecordRepository.save(toRecord(state));
            ratingService.applyResult(state.gameId().toString());
        }
        gameStateCache.storeGameState(state.gameId(), state);
    }

    /** Stops the player's unfinished games against the computer: no winner, the clock stopped, nothing rated. */
    private void abortComputerGames(Player player) {
        if (player.isComputer()) {
            return;
        }
        for (UUID gameId : gameStateCache.gamesInProgress(player)) {
            synchronized (lock(gameId)) {
                GameState state = loadState(gameId);
                if (state != null && !state.isOver() && !state.isOnline()) {
                    finish(state, ABORTED, null, Instant.now());
                }
            }
        }
    }

    /** The player's games in progress. */
    private List<GameState> inProgress(Player player) {
        return gameStateCache.gamesInProgress(player).stream()
                .map(this::loadState)
                .filter(state -> state != null && !state.isOver() && state.hasPlayer(player))
                .toList();
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

    /** The finished game as the database keeps it. */
    private GameRecord toRecord(GameState state) {
        GameRecord record = new GameRecord(state.gameId().toString(), state.white().id(), state.black().id(),
                account(state.white()), account(state.black()), state.status(), state.boardState(), state.createdAt());
        record.setWinner(state.winner());
        record.setMoves(String.join(" ", state.moves()));
        record.setUpdatedAt(state.updatedAt());
        record.setRated(state.rated());
        record.setWhiteRating(state.whiteRating());
        record.setBlackRating(state.blackRating());
        if (state.isTimed()) {
            record.setClockInitialMs(state.clock().initialMs());
            record.setClockIncrementMs(state.clock().incrementMs());
        }
        writeClock(record, state.clock());
        return record;
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

    private static boolean canClaimWin(GameState state, Player player) {
        return state.isOnline() && !state.isTimed() && !state.isOver()
                && !state.playerToMove().id().equals(player.id())
                && Duration.between(state.updatedAt(), Instant.now()).compareTo(ABANDON_AFTER) >= 0;
    }

    private static boolean isWhite(GameState state, Player player) {
        return state.white().id().equals(player.id());
    }

    /** One side of the board as players see it: the current username, so renames show in every game. */
    private LiveGame.Side side(Player player, Integer rating) {
        String name = player.isComputer() ? "Computer"
                : Optional.ofNullable(account(player)).map(UserAccount::displayName).orElse("Guest");
        return new LiveGame.Side(name, rating);
    }

    private GameSummary summary(GameState state, Player player) {
        Player opponent = isWhite(state, player) ? state.black() : state.white();
        return GameSummary.of(state, player, side(opponent, null).name());
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

    /**
     * The game from the cache or else the database; null if there is no such game. The database has
     * finished games, and games in progress from before they were kept only in the cache.
     */
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
                record.getFen(), record.getStatus(), clock, record.getWinner(),
                record.getMoves().isEmpty() ? List.of() : List.of(record.getMoves().split(" ")),
                record.isRated(), record.getWhiteRating(), record.getBlackRating(),
                record.getCreatedAt(), record.getUpdatedAt());
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
