package com.jreynolds.game.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class InMemoryGameStateCache implements GameStateCache {

    private final Cache<UUID,String> gameStateCache;


    public InMemoryGameStateCache(@Value("${game.state.cache.duration:PT5M}") Duration duration) {
        this.gameStateCache = Caffeine.newBuilder()
                .expireAfterWrite(duration)
                .build();
    }

    @Override
    public void storeGameState(UUID gameId, String state) {
        gameStateCache.put(gameId, state);
    }

    @Override
    public String getGameState(UUID gameId) {
       return gameStateCache.getIfPresent(gameId);
    }

    @Override
    public void removeGameState(UUID gameId) {
        gameStateCache.invalidate(gameId);
    }
}
