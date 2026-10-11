package com.jblazing.game.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jblazing.model.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Games in play, kept in Redis as JSON so every server instance sees the same state and it
 * survives restarts. Entries expire {@code game.state.cache.duration} after their last write;
 * the database stays the source of truth, so an expired or unreadable entry is simply reloaded.
 */
@Component
public class RedisGameStateCache implements GameStateCache {

    private static final Logger logger = LoggerFactory.getLogger(RedisGameStateCache.class);
    static final String KEY_PREFIX = "chess:game-state:";

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

    @Override
    public void storeGameState(UUID gameId, GameState state) {
        try {
            redis.opsForValue().set(key(gameId), mapper.writeValueAsString(state), duration);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize the state of game " + gameId, e);
        }
    }

    @Override
    public GameState getGameState(UUID gameId) {
        String json = redis.opsForValue().get(key(gameId));
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, GameState.class);
        } catch (JsonProcessingException e) {
            // e.g. written by an older version of GameState: drop it and let the database answer
            logger.warn("Discarding unreadable cached state of game {}", gameId, e);
            redis.delete(key(gameId));
            return null;
        }
    }

    @Override
    public void removeGameState(UUID gameId) {
        redis.delete(key(gameId));
    }
}
