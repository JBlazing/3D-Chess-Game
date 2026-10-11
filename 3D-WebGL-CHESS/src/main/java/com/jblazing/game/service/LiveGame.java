package com.jblazing.game.service;

import java.util.List;

/**
 * A game as one of its players sees it, pushed to them while it is being played.
 *
 * @param color               your side, WHITE or BLACK
 * @param status              IN_PROGRESS, CHECKMATE, STALEMATE, DRAW, RESIGNED, ABANDONED, TIMEOUT or ABORTED
 * @param winner              WHITE or BLACK when there is one, otherwise null
 * @param moves               every move so far in SAN; the client applies the ones it hasn't seen
 * @param opponentIdleSeconds how long the opponent has had the move, while it is their turn
 * @param canClaimWin         the opponent has gone {@link GameService#ABANDON_AFTER} without moving (untimed games only)
 * @param ratingChange        how much your rating changed, once a rated game has ended
 * @param clock               the blitz clock, for timed games; null for untimed games
 */
public record LiveGame(String gameId, String color, Side white, Side black, boolean online, boolean rated,
                       String status, String winner, String fen, List<String> moves,
                       long opponentIdleSeconds, boolean canClaimWin, Integer ratingChange, Clock clock) {

    /** @param rating the rating the player started the game with; null for the computer and guests */
    public record Side(String name, Integer rating) {
    }

    /**
     * The clock as it reads right now, computed by the server.
     *
     * @param timeControl e.g. "5+3": minutes each, seconds added per move
     * @param running     whose clock is running, WHITE or BLACK; null once the game is over
     */
    public record Clock(String timeControl, long whiteMs, long blackMs, String running) {
    }
}
