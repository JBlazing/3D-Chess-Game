package com.jblazing.matchmaking;

import com.jblazing.player.Player;

public interface MatchMakingService {

    /** Puts the player in the queue (or keeps them there) and returns where they stand. */
    QueueStatus joinQueue(Player player);

    boolean leaveQueue(Player player);

    /** Where the player stands: not queued, still searching, or matched into a game. Also keeps their place. */
    QueueStatus status(Player player);

    /**
     * Get the size queue
     *
     * @return
     */
    int getQueueSize();
}
