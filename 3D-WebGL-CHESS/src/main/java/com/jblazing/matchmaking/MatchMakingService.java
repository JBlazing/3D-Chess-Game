package com.jblazing.matchmaking;

import com.jblazing.player.Player;

public interface MatchMakingService {

    boolean joinQueue(Player player);

    boolean leaveQueue(Player player);

    /**
     * Get the size queue
     *
     * @return
     */
    int getQueueSize();
}
