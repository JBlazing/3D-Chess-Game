package com.jblazing.matchmaking;

import com.jblazing.game.service.GameService;
import com.jblazing.player.Player;
import com.jblazing.rating.RatingService;
import com.jblazing.user.UserAccount;
import com.jblazing.user.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Pairs waiting players by rating.
 *
 * <p>Each player accepts opponents within a window around their rating that starts at
 * {@link #INITIAL_RANGE} and widens by {@link #RANGE_GROWTH_PER_SECOND} for every second waited,
 * up to {@link #MAX_RANGE}. Two players are paired only when each is inside the other's window, so a
 * long wait never drags a new arrival into a lopsided game. The longest-waiting players are paired
 * first, each with the closest-rated eligible opponent. Signed-in players are only paired with each
 * other (rated games); guests, when sign-in is disabled, likewise (unrated).
 *
 * <p>Players stay queued while their page polls {@link #status}; one that stops (a closed tab)
 * is dropped after {@link #STALE_AFTER}.
 */
public class EloMatchMaking implements MatchMakingService {
    private static final Logger logger = LoggerFactory.getLogger(EloMatchMaking.class);

    static final int INITIAL_RANGE = 100;
    static final int RANGE_GROWTH_PER_SECOND = 15;
    static final int MAX_RANGE = 500;
    static final Duration STALE_AFTER = Duration.ofSeconds(15);
    static final Duration KEEP_MATCHES = Duration.ofMinutes(10);

    private final GameService gameService;
    private final UserAccountRepository userAccountRepository;
    private final Random random = new Random();
    private final Clock clock;

    private final Map<String, Entry> queue = new LinkedHashMap<>();
    private final Map<String, Match> matches = new HashMap<>();

    private static final class Entry {
        final Player player;
        final int rating;
        final String name;
        final Instant joinedAt;
        Instant lastSeen;

        Entry(Player player, int rating, String name, Instant now) {
            this.player = player;
            this.rating = rating;
            this.name = name;
            this.joinedAt = now;
            this.lastSeen = now;
        }

        boolean rated() {
            return player.accountId() != null;
        }

        int range(Instant now) {
            long waited = Duration.between(joinedAt, now).toSeconds();
            return (int) Math.min(MAX_RANGE, INITIAL_RANGE + RANGE_GROWTH_PER_SECOND * waited);
        }
    }

    private record Match(String gameId, String color, String opponentName, Integer opponentRating, Instant at) {
    }

    public EloMatchMaking(GameService gameService, UserAccountRepository userAccountRepository) {
        this(gameService, userAccountRepository, Clock.systemUTC());
    }

    /** With a given clock, so tests can let time pass without waiting. */
    EloMatchMaking(GameService gameService, UserAccountRepository userAccountRepository, Clock clock) {
        this.gameService = gameService;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    @Override
    public synchronized QueueStatus joinQueue(Player player) {
        Instant now = Instant.now(clock);
        matches.remove(player.id());
        Entry entry = queue.get(player.id());
        if (entry == null) {
            Optional<UserAccount> account = Optional.ofNullable(player.accountId()).flatMap(userAccountRepository::findById);
            // opponents are shown your username, so signed-in players need one before playing online
            if (account.isPresent() && account.get().getUsername() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Choose a username first");
            }
            entry = new Entry(player,
                    account.map(UserAccount::getRating).orElse(RatingService.STARTING_RATING),
                    account.map(UserAccount::displayName).orElse("Guest"), now);
            queue.put(player.id(), entry);
            logger.info("{} joined the queue (rating {}{})", player.id(), entry.rating, entry.rated() ? "" : ", unrated guest");
        }
        entry.lastSeen = now;
        return searching(entry, now);
    }

    @Override
    public synchronized boolean leaveQueue(Player player) {
        return queue.remove(player.id()) != null;
    }

    @Override
    public synchronized QueueStatus status(Player player) {
        Instant now = Instant.now(clock);
        Match match = matches.get(player.id());
        if (match != null) {
            return new QueueStatus("MATCHED", null, 0, 0, match.gameId(), match.color(), match.opponentName(), match.opponentRating());
        }
        Entry entry = queue.get(player.id());
        if (entry == null) {
            return QueueStatus.idle();
        }
        entry.lastSeen = now;
        return searching(entry, now);
    }

    @Override
    public synchronized int getQueueSize() {
        return queue.size();
    }

    /** Pairs whoever can be paired. Runs every second. */
    @Scheduled(fixedDelay = 1000)
    public synchronized void createGames() {
        Instant now = Instant.now(clock);
        queue.values().removeIf(e -> {
            boolean stale = Duration.between(e.lastSeen, now).compareTo(STALE_AFTER) > 0;
            if (stale) logger.info("{} dropped from the queue (stopped polling)", e.player.id());
            return stale;
        });
        matches.values().removeIf(m -> Duration.between(m.at(), now).compareTo(KEEP_MATCHES) > 0);

        // longest wait first (the map keeps join order)
        List<Entry> waiting = new ArrayList<>(queue.values());
        Set<Entry> paired = new HashSet<>();
        for (Entry a : waiting) {
            if (paired.contains(a)) continue;
            Entry best = null;
            for (Entry b : waiting) {
                if (b == a || paired.contains(b) || b.rated() != a.rated()) continue;
                int gap = Math.abs(a.rating - b.rating);
                if (gap > a.range(now) || gap > b.range(now)) continue;
                if (best == null || gap < Math.abs(a.rating - best.rating)) best = b;
            }
            if (best != null) {
                paired.add(a);
                paired.add(best);
                start(a, best, now);
            }
        }
        paired.forEach(e -> queue.remove(e.player.id()));
    }

    private void start(Entry a, Entry b, Instant now) {
        boolean aWhite = random.nextBoolean();
        Entry white = aWhite ? a : b, black = aWhite ? b : a;
        String gameId = gameService.createNewGame(white.player, black.player).toString();
        matches.put(white.player.id(), new Match(gameId, "WHITE", black.name, black.rated() ? black.rating : null, now));
        matches.put(black.player.id(), new Match(gameId, "BLACK", white.name, white.rated() ? white.rating : null, now));
        logger.info("Matched {} ({}) vs {} ({}) in game {}", white.player.id(), white.rating, black.player.id(), black.rating, gameId);
    }

    private static QueueStatus searching(Entry entry, Instant now) {
        return new QueueStatus("SEARCHING", entry.rated() ? entry.rating : null, entry.range(now),
                Duration.between(entry.joinedAt, now).toSeconds(), null, null, null, null);
    }
}
