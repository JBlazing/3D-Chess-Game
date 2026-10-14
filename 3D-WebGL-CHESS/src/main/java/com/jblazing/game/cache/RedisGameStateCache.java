package com.jblazing.game.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jblazing.model.GameState;
import com.jblazing.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Games kept in Redis as JSON, so every server instance sees the same games and they survive
 * restarts (Redis must persist its data, e.g. with appendonly). A game in progress is kept until it
 * ends, with its id in a set of all games in progress and in a set per player; a finished game
 * expires {@code game.state.cache.duration} after it ended, and is then read from the database.
 */
@Component
public class RedisGameStateCache implements GameStateCache {

    private static final Logger logger = LoggerFactory.getLogger(RedisGameStateCache.class);
    static final String KEY_PREFIX = "chess:game-state:";
    static final String IN_PROGRESS_KEY = "chess:games-in-progress";
    static final String PLAYER_KEY_PREFIX = "chess:player-games-in-progress:";

    private final StringRedisTemplate redis;
    private final Duration duration;
    private final ObjectMapper mapper;

    public RedisGameStateCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                               @Value("${game.state.cache.duration:PT5M}") Duration duration) {
        this.redis = redis;
        this.duration = duration;
        // the records' components only, not derived values such as GameState.isOver()
        this.mapper = objectMapper.copy()
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    static String key(UUID gameId) {
        return KEY_PREFIX + gameId;
    }

    static String playerKey(Player player) {
        return PLAYER_KEY_PREFIX + player.id();
    }

    @Override
    public void storeGameState(UUID gameId, GameState state) {
        String json;
        try {
            json = mapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize the state of game " + gameId, e);
        }
        String id = gameId.toString();
        List<String> playerKeys = Stream.of(state.white(), state.black())
                .filter(player -> !player.isComputer())
                .map(RedisGameStateCache::playerKey)
                .toList();
        // the game and its index entries change together
        redis.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) throws DataAccessException {
                RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                ops.multi();
                if (state.isOver()) {
                    ops.opsForValue().set(key(gameId), json, duration);
                    ops.opsForSet().remove(IN_PROGRESS_KEY, id);
                    playerKeys.forEach(playerKey -> ops.opsForSet().remove(playerKey, id));
                } else {
                    ops.opsForValue().set(key(gameId), json);   // no expiry: this is the only copy
                    ops.opsForSet().add(IN_PROGRESS_KEY, id);
                    playerKeys.forEach(playerKey -> ops.opsForSet().add(playerKey, id));
                }
                return ops.exec();
            }
        });
    }

    @Override
    public GameState getGameState(UUID gameId) {
        String json = redis.opsForValue().get(key(gameId));
        if (json == null) {
            return null;
        }
        GameState state;
        try {
            state = mapper.readValue(json, GameState.class);
        } catch (JsonProcessingException e) {
            // e.g. written by an older version of GameState: drop it and let the database answer
            logger.warn("Discarding unreadable cached state of game {}", gameId, e);
            redis.delete(key(gameId));
            return null;
        }
        if (state.updatedAt() == null) {
            // written by the version that saved every move to the database, which has the whole game
            logger.info("Discarding old-format cached state of game {}", gameId);
            redis.delete(key(gameId));
            return null;
        }
        return state;
    }

    @Override
    public void removeGameState(UUID gameId) {
        redis.delete(key(gameId));
        redis.opsForSet().remove(IN_PROGRESS_KEY, gameId.toString());
    }

    @Override
    public Set<UUID> gamesInProgress() {
        return ids(redis.opsForSet().members(IN_PROGRESS_KEY));
    }

    @Override
    public Set<UUID> gamesInProgress(Player player) {
        return ids(redis.opsForSet().members(playerKey(player)));
    }

    private static Set<UUID> ids(Set<String> members) {
        return members == null ? Set.of() : members.stream().map(UUID::fromString).collect(Collectors.toSet());
    }
}
