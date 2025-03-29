package com.jblazing.matchmaking;

import java.util.UUID;

public interface MatchMakingService {

    boolean joinQueue(UUID playerId);

    boolean leaveQueue(UUID playerId);

    int getQueueSize();
}
