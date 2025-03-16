package com.jblazing.game.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jblazing.model.GameState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class InMemoryGameStateCache implements GameStateCache {

    private final Cache<UUID, GameState> gameStateCache;


    public InMemoryGameStateCache(@Value("${game.state.cache.duration:PT5M}") Duration duration) {
        this.gameStateCache = Caffeine.newBuilder()
                .expireAfterWrite(duration)
                .build();
    }

    @Override
    public void storeGameState(UUID gameId, GameState state) {
        gameStateCache.put(gameId, state);
    }

    @Override
    public GameState getGameState(UUID gameId) {
       return gameStateCache.getIfPresent(gameId);
    }

    @Override
    public void removeGameState(UUID gameId) {
        gameStateCache.invalidate(gameId);
    }
}
