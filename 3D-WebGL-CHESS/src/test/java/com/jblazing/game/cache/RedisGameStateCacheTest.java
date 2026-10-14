package com.jblazing.game.cache;

import com.jblazing.model.GameClock;
import com.jblazing.model.GameState;
import com.jblazing.model.TimeControl;
import com.jblazing.player.Player;
import com.jblazing.support.TestDatabases;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Game states go to Redis and come back unchanged; games in progress are kept and indexed until they
 * end, finished ones expire; on a real Redis server.
 */
class RedisGameStateCacheTest {

    private static final Duration DURATION = Duration.ofMinutes(10);
    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");

    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;
    private static RedisGameStateCache cache;

    @BeforeAll
    static void connect() {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(
                TestDatabases.redis().getHost(), TestDatabases.redis().getMappedPort(6379));
        server.setPassword(TestDatabases.REDIS_PASSWORD);
        connections = new LettuceConnectionFactory(server);
        connections.afterPropertiesSet();
        connections.start();
        redis = new StringRedisTemplate(connections);
        // the same ObjectMapper setup the app gets from Spring Boot
        cache = new RedisGameStateCache(redis, Jackson2ObjectMapperBuilder.json().build(), DURATION);
    }

    @AfterAll
    static void disconnect() {
        connections.destroy();
    }

    private static GameState timedGame() {
        return timedGame(Player.account(7), Player.guest(UUID.randomUUID().toString()));
    }

    /** A timed game in progress, one move played. */
    private static GameState timedGame(Player white, Player black) {
        GameClock clock = GameClock.start(new TimeControl(Duration.ofMinutes(5), Duration.ofSeconds(3)), START)
                .afterMove(true, START.plusMillis(20_123));
        return new GameState(UUID.randomUUID(), white, black,
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", GameState.IN_PROGRESS, clock,
                null, List.of("e4"), false, 1250, null, START, START.plusMillis(20_123));
    }

    private static GameState finished(GameState state) {
        return state.ended("RESIGNED", "WHITE", state.clock().stopped(state.whiteToMove(), START.plusSeconds(30)), START.plusSeconds(30));
    }

    @Test
    void aStoredStateComesBackEqual() {
        GameState state = timedGame();

        cache.storeGameState(state.gameId(), state);

        assertThat(cache.getGameState(state.gameId())).isEqualTo(state);
    }

    @Test
    void untimedGamesAgainstTheComputer() {
        GameState state = new GameState(UUID.randomUUID(), Player.COMPUTER, Player.account(3),
                GameState.STARTING_POSITION, "CHECKMATE", null, "WHITE", List.of(), false, null, 1200, START, START);

        cache.storeGameState(state.gameId(), state);

        GameState loaded = cache.getGameState(state.gameId());
        assertThat(loaded).isEqualTo(state);
        assertThat(loaded.white()).isEqualTo(Player.COMPUTER).matches(Player::isComputer);
        assertThat(loaded.isTimed()).isFalse();
    }

    @Test
    void onlyTheRecordComponentsAreStored() {
        GameState state = timedGame();

        cache.storeGameState(state.gameId(), state);

        // derived values (isOver(), isTimed(), isComputer()...) aren't written
        assertThat(redis.opsForValue().get(RedisGameStateCache.key(state.gameId())))
                .contains("\"boardState\"", "\"turnStartedAt\"", "\"moves\"")
                .doesNotContain("\"over\"", "\"timed\"", "\"computer\"", "\"online\"");
    }

    @Test
    void aGameInProgressNeverExpires() {
        GameState state = timedGame();

        cache.storeGameState(state.gameId(), state);

        assertThat(redis.getExpire(RedisGameStateCache.key(state.gameId()))).as("no expiry").isEqualTo(-1);
    }

    @Test
    void aFinishedGameExpiresAfterTheConfiguredDuration() {
        GameState state = timedGame();
        cache.storeGameState(state.gameId(), state);

        cache.storeGameState(state.gameId(), finished(state));

        Duration ttl = Duration.ofSeconds(redis.getExpire(RedisGameStateCache.key(state.gameId())));
        assertThat(ttl).isPositive().isLessThanOrEqualTo(DURATION);
    }

    @Test
    void gamesInProgressAreIndexedForEachPlayerUntilTheyEnd() {
        Player alice = Player.account(System.nanoTime()), guest = Player.guest(UUID.randomUUID().toString());
        GameState online = timedGame(alice, guest), computer = timedGame(Player.COMPUTER, alice);

        cache.storeGameState(online.gameId(), online);
        cache.storeGameState(computer.gameId(), computer);

        assertThat(cache.gamesInProgress()).contains(online.gameId(), computer.gameId());
        assertThat(cache.gamesInProgress(alice)).containsExactlyInAnyOrder(online.gameId(), computer.gameId());
        assertThat(cache.gamesInProgress(guest)).containsExactly(online.gameId());
        assertThat(redis.hasKey(RedisGameStateCache.playerKey(Player.COMPUTER))).as("the computer isn't indexed").isFalse();

        cache.storeGameState(online.gameId(), finished(online));

        assertThat(cache.gamesInProgress()).doesNotContain(online.gameId()).contains(computer.gameId());
        assertThat(cache.gamesInProgress(alice)).containsExactly(computer.gameId());
        assertThat(cache.gamesInProgress(guest)).isEmpty();
    }

    @Test
    void anUnknownGameIsNull() {
        assertThat(cache.getGameState(UUID.randomUUID())).isNull();
    }

    @Test
    void aRemovedGameIsGone() {
        GameState state = timedGame();
        cache.storeGameState(state.gameId(), state);

        cache.removeGameState(state.gameId());

        assertThat(cache.getGameState(state.gameId())).isNull();
        assertThat(cache.gamesInProgress()).doesNotContain(state.gameId());
    }

    @Test
    void anUnreadableEntryIsDiscarded() {
        UUID gameId = UUID.randomUUID();
        redis.opsForValue().set(RedisGameStateCache.key(gameId), "{\"not\": \"a game state\"");

        assertThat(cache.getGameState(gameId)).as("falls back to the database").isNull();
        assertThat(redis.hasKey(RedisGameStateCache.key(gameId))).isFalse();
    }

    @Test
    void anEntryFromBeforeGamesWereKeptOnlyHereIsDiscarded() {
        UUID gameId = UUID.randomUUID();
        // what the previous version wrote: no moves or times, which the database had
        redis.opsForValue().set(RedisGameStateCache.key(gameId), "{\"gameId\":\"" + gameId + "\","
                + "\"white\":{\"id\":\"user:1\",\"accountId\":1},\"black\":{\"id\":\"COMPUTER\",\"accountId\":null},"
                + "\"boardState\":\"" + GameState.STARTING_POSITION + "\",\"status\":\"IN_PROGRESS\",\"clock\":null}");

        assertThat(cache.getGameState(gameId)).as("falls back to the database").isNull();
        assertThat(redis.hasKey(RedisGameStateCache.key(gameId))).isFalse();
    }
}
