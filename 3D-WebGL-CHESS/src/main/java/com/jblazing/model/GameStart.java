package com.jblazing.model;

import java.util.UUID;

public record GameStart(UUID gameId, String playerId, String opponentPlayer, Move startingMove) {
}
