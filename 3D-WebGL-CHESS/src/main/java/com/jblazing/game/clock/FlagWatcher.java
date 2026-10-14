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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Ends timed games when a flag falls, whether or not anyone is watching: each running clock has a
 * check scheduled for the moment the side to move runs out of time, moved whenever the game changes.
 * Games still being played when the server starts are picked up again.
 */
@Component
public class FlagWatcher {
    private static final Logger logger = LoggerFactory.getLogger(FlagWatcher.class);

    static final Duration RETRY_AFTER = Duration.ofSeconds(1);

    private final GameService gameService;
    private final TaskScheduler scheduler;
    private final Map<UUID, ScheduledFuture<?>> flags = new ConcurrentHashMap<>();

    public FlagWatcher(GameService gameService, TaskScheduler scheduler) {
        this.gameService = gameService;
        this.scheduler = scheduler;
    }

    @EventListener
    public void onGameChanged(GameChanged event) {
        watch(event.gameId(), event.state());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void watchGamesInProgress() {
        gameService.gamesInProgress().forEach(gameId -> gameService.stateOf(gameId).ifPresent(state -> watch(gameId, state)));
    }

    /** Schedules the game's flag check for its current deadline, replacing any earlier one; none once the game is over. */
    void watch(UUID gameId, GameState state) {
        flags.compute(gameId, (id, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }
            if (state.isOver() || !state.isTimed()) {
                return null;
            }
            return scheduler.schedule(() -> check(id), state.clock().deadline(state.whiteToMove()));
        });
    }

    private void check(UUID gameId) {
        try {
            if (!gameService.flagIfOutOfTime(gameId)) {
                // the game moved on meanwhile, or the check ran a moment early: look at its deadline again
                gameService.stateOf(gameId).ifPresent(state -> watch(gameId, state));
            }
        } catch (RuntimeException e) {
            logger.warn("Couldn't check the clock of game {}; trying again", gameId, e);
            flags.put(gameId, scheduler.schedule(() -> check(gameId), Instant.now().plus(RETRY_AFTER)));
        }
    }
}
