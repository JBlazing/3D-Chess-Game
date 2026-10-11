package com.jblazing.game.service;

import com.jblazing.model.GameState;

import java.util.UUID;

/**
 * Published by {@link GameService} whenever a game changes: it started, a move was played, or it
 * ended. Listeners push the new state to the players and keep the game's flag watched.
 *
 * @param state the game as it now stands
 */
public record GameChanged(UUID gameId, GameState state) {
}
