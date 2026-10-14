package com.jblazing.game.cache;

import com.jblazing.model.GameState;
import com.jblazing.player.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Where games are kept while they are played: the only copy of a game in progress, which the
 * database only gets once it is over. A finished game stays here for a while too, so its last
 * state can still be shown, and is then only in the database.
 */
public interface GameStateCache {

    /** Keeps the game, and indexes it while it is in progress; a finished game is no longer indexed. */
    void storeGameState(UUID gameId, GameState state);

    GameState getGameState(UUID gameId);

    void removeGameState(UUID gameId);

    /** Every game in progress. */
    Set<UUID> gamesInProgress();

    /** The player's games in progress. */
    Set<UUID> gamesInProgress(Player player);
}
