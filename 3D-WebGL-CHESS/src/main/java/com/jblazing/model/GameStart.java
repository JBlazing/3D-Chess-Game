package com.jblazing.model;

import java.util.UUID;

public record GameStart(UUID gameId, UUID playerId, String opponentPlayer, Move startingMove) {
}
