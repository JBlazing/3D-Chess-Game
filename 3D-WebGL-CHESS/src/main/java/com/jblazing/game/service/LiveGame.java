package com.jblazing.game.service;

import java.util.List;

/**
 * A game as one of its players sees it, polled while it is being played.
 *
 * @param color               your side, WHITE or BLACK
 * @param status              IN_PROGRESS, CHECKMATE, STALEMATE, DRAW, RESIGNED or ABANDONED
 * @param winner              WHITE or BLACK when there is one, otherwise null
 * @param moves               every move so far in SAN; the client applies the ones it hasn't seen
 * @param opponentIdleSeconds how long the opponent has had the move, while it is their turn
 * @param canClaimWin         the opponent has gone {@link GameService#ABANDON_AFTER} without moving
 * @param ratingChange        how much your rating changed, once a rated game has ended
 */
public record LiveGame(String gameId, String color, Side white, Side black, boolean online, boolean rated,
                       String status, String winner, String fen, List<String> moves,
                       long opponentIdleSeconds, boolean canClaimWin, Integer ratingChange) {

    /** @param rating the rating the player started the game with; null for the computer and guests */
    public record Side(String name, Integer rating) {
    }
}
