package com.jblazing.game.events;

import com.jblazing.game.service.GameChanged;
import com.jblazing.game.service.GameService;
import com.jblazing.game.service.LiveGame;
import com.jblazing.player.Player;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pushes games to their players as Server-Sent Events, so the page never has to poll:
 * <ul>
 *   <li>{@code state}: the whole game from the player's side ({@link LiveGame}), when they connect
 *       and whenever it changes. The stream ends after the game does.</li>
 *   <li>{@code clock}: the blitz clock as the server reads it ({@link LiveGame.Clock}), every second
 *       while it runs.</li>
 * </ul>
 * Each event is worked out just before it is sent, one at a time per connection, so a player never
 * gets an older reading after a newer one. A browser that drops the connection reconnects by itself
 * and gets the current state straight away.
 */
@Component
public class GameEventStream {
    private static final Logger logger = LoggerFactory.getLogger(GameEventStream.class);

    /** A connection is closed after this long; the browser opens a new one. */
    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);
    /** How soon the browser should reconnect when the connection drops. */
    static final Duration RECONNECT_AFTER = Duration.ofSeconds(2);

    private final GameService gameService;
    private final Map<UUID, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();
    // sending writes to the network, which must not hold up the move (or the scheduler) that caused it
    private final ExecutorService sender = Executors.newFixedThreadPool(4, new ThreadFactory());

    private static final class Subscriber {
        final UUID gameId;
        final Player player;
        final SseEmitter emitter;
        volatile boolean closed;

        Subscriber(UUID gameId, Player player, SseEmitter emitter) {
            this.gameId = gameId;
            this.player = player;
            this.emitter = emitter;
        }
    }

    private interface Event {
        Object read();
    }

    public GameEventStream(GameService gameService) {
        this.gameService = gameService;
    }

    /** Starts streaming the game to one of its players; anyone else gets a 404, as for any other look at the game. */
    public SseEmitter subscribe(UUID gameId, Player player) {
        gameService.liveGame(gameId, player);   // throws for anyone not in the game
        Subscriber subscriber = new Subscriber(gameId, player, new SseEmitter(STREAM_TIMEOUT.toMillis()));
        subscriber.emitter.onCompletion(() -> remove(subscriber));
        subscriber.emitter.onTimeout(() -> remove(subscriber));
        subscriber.emitter.onError(e -> remove(subscriber));
        // listening before the first state is read, so no change can slip in between
        subscribers.computeIfAbsent(gameId, id -> ConcurrentHashMap.newKeySet()).add(subscriber);
        sendState(subscriber);
        return subscriber.emitter;
    }

    @EventListener
    public void onGameChanged(GameChanged event) {
        Set<Subscriber> watching = subscribers.get(event.gameId());
        if (watching != null) {
            watching.forEach(subscriber -> sender.execute(() -> sendState(subscriber)));
        }
    }

    /** The clocks, every second, to everyone watching a game whose clock is running. */
    @Scheduled(fixedRate = 1000)
    public void tick() {
        subscribers.forEach((gameId, watching) -> {
            if (gameService.clockNow(gameId).map(LiveGame.Clock::running).isEmpty()) {
                return;
            }
            watching.forEach(subscriber -> sender.execute(() ->
                    send(subscriber, "clock", () -> gameService.clockNow(gameId).orElse(null))));
        });
    }

    int subscriberCount(UUID gameId) {
        return subscribers.getOrDefault(gameId, Set.of()).size();
    }

    /** The game as it now stands; once it is over that is the last event, and the stream ends. */
    private void sendState(Subscriber subscriber) {
        send(subscriber, "state", () -> gameService.liveGame(subscriber.gameId, subscriber.player));
    }

    /** Reads the event's data and sends it. Drops the connection if it has gone. */
    private void send(Subscriber subscriber, String name, Event event) {
        synchronized (subscriber) {
            if (subscriber.closed) {
                return;
            }
            try {
                Object data = event.read();
                if (data != null) {
                    subscriber.emitter.send(SseEmitter.event().name(name).data(data).reconnectTime(RECONNECT_AFTER.toMillis()));
                }
                if (data instanceof LiveGame game && !"IN_PROGRESS".equals(game.status())) {
                    remove(subscriber);
                    subscriber.emitter.complete();
                }
            } catch (IOException e) {
                // the browser went away (e.g. the page left this game for a new one). Spring MVC and
                // the servlet container clean up a failed send themselves; completing it here would
                // report the closed connection as a server error
                logger.debug("Dropping the {} stream of game {} for {}: {}", name, subscriber.gameId, subscriber.player.id(), e.toString());
                remove(subscriber);
            } catch (RuntimeException e) {
                // the game can't be read: end the stream; the browser reconnects if it is still there
                logger.warn("Ending the {} stream of game {} for {}", name, subscriber.gameId, subscriber.player.id(), e);
                remove(subscriber);
                try {
                    subscriber.emitter.completeWithError(e);
                } catch (RuntimeException alreadyClosed) {
                    // nothing left to close
                }
            }
        }
    }

    /** Stops sending to the subscriber; also called back when its connection completes, times out or fails. */
    private void remove(Subscriber subscriber) {
        subscriber.closed = true;
        subscribers.computeIfPresent(subscriber.gameId, (id, watching) -> {
            watching.remove(subscriber);
            return watching.isEmpty() ? null : watching;
        });
    }

    @PreDestroy
    void shutdown() {
        sender.shutdownNow();
    }

    private static final class ThreadFactory implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "game-events-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
