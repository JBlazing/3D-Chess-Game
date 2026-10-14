package com.jblazing.game.clock;

import com.jblazing.game.service.GameChanged;
import com.jblazing.game.service.GameService;
import com.jblazing.model.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Plays the computer's moves. Whenever it becomes the computer's turn, its move is scheduled after a
 * human-like thinking delay ({@link #thinkingDelay}), which its clock pays for like anyone's time.
 * The move is pushed to the player over the game's event stream; the page never asks for it.
 */
@Component
public class ComputerOpponent {
    private static final Logger logger = LoggerFactory.getLogger(ComputerOpponent.class);

    /** Above this much time left the computer takes its time; below it, it hurries. */
    static final long PLENTY_OF_TIME_MS = 30_000;
    static final long SLOW_MIN_MS = 5_000, SLOW_MAX_MS = 10_000;
    static final long FAST_MIN_MS = 1_000, FAST_MAX_MS = 3_000;
    static final Duration RETRY_AFTER = Duration.ofSeconds(1);

    private final GameService gameService;
    private final TaskScheduler scheduler;
    private final RandomGenerator random = RandomGenerator.getDefault();

    public ComputerOpponent(GameService gameService, TaskScheduler scheduler) {
        this.gameService = gameService;
        this.scheduler = scheduler;
    }

    @EventListener
    public void onGameChanged(GameChanged event) {
        watch(event.gameId(), event.state());
    }

    /** After a restart: computer moves that were due when the server stopped. */
    @EventListener(ApplicationReadyEvent.class)
    public void watchGamesInProgress() {
        gameService.gamesInProgress().forEach(gameId -> gameService.stateOf(gameId).ifPresent(state -> watch(gameId, state)));
    }

    /**
     * How long the computer "thinks" with {@code msLeft} on its clock (null: untimed): 5-10 seconds
     * with more than 30 seconds left, 1-3 seconds with less. Never more than half its time, so the
     * delay alone can't make it lose on time.
     */
    static Duration thinkingDelay(Long msLeft, RandomGenerator random) {
        boolean plenty = msLeft == null || msLeft > PLENTY_OF_TIME_MS;
        long delay = plenty ? random.nextLong(SLOW_MIN_MS, SLOW_MAX_MS + 1) : random.nextLong(FAST_MIN_MS, FAST_MAX_MS + 1);
        return Duration.ofMillis(msLeft == null ? delay : Math.min(delay, msLeft / 2));
    }

    private void watch(UUID gameId, GameState state) {
        if (state.isOver() || !state.playerToMove().isComputer()) {
            return;
        }
        Long msLeft = state.isTimed() ? (state.whiteToMove() ? state.clock().whiteMs() : state.clock().blackMs()) : null;
        Duration delay = thinkingDelay(msLeft, random);
        // from the start of the computer's turn, so time spent before this ran (e.g. a restart) counts
        Instant due = (state.isTimed() ? state.clock().turnStartedAt() : Instant.now()).plus(delay);
        scheduleMove(gameId, state.boardState(), due);
    }

    private void scheduleMove(UUID gameId, String position, Instant due) {
        scheduler.schedule(() -> {
            try {
                gameService.playComputerMove(gameId, position);
            } catch (RuntimeException e) {
                // e.g. the engine is busy: try again shortly; meanwhile the computer's clock keeps running
                logger.warn("The computer couldn't move in game {}; trying again", gameId, e);
                scheduleMove(gameId, position, Instant.now().plus(RETRY_AFTER));
            }
        }, due);
    }
}
