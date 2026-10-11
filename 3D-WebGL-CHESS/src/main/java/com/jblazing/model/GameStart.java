package com.jblazing.model;

import java.util.UUID;

/** A new game against the computer. When the computer has white, its opening move arrives over the game's event stream. */
public record GameStart(UUID gameId, String playerId, String opponentPlayer) {
}
