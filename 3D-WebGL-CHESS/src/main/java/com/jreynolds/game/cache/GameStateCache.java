package com.jreynolds.game.cache;

import com.jreynolds.model.GameState;

import java.util.Optional;
import java.util.UUID;

public interface GameStateCache {

    void storeGameState(UUID gameId, GameState state);

    GameState getGameState(UUID gameId);

    void removeGameState(UUID gameId);

}
