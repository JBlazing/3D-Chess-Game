package com.jreynolds.game.cache;

import java.util.Optional;
import java.util.UUID;

public interface GameStateCache {

    void storeGameState(UUID gameId, String state);

    String getGameState(UUID gameId);

    void removeGameState(UUID gameId);

}
