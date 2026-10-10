package com.jblazing.matchmaking;

/**
 * @param state          IDLE (not queued), SEARCHING or MATCHED
 * @param rating         the rating used for pairing; null for guests, who are paired with each other unrated
 * @param range          how far from that rating an opponent may currently be (it widens while you wait)
 * @param waitedSeconds  time in the queue so far
 * @param gameId         the game you were matched into, once MATCHED
 * @param color          your side in that game, WHITE or BLACK
 */
public record QueueStatus(String state, Integer rating, int range, long waitedSeconds,
                          String gameId, String color, String opponentName, Integer opponentRating) {

    static QueueStatus idle() {
        return new QueueStatus("IDLE", null, 0, 0, null, null, null, null);
    }
}
