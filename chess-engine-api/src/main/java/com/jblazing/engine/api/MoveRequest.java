package com.jblazing.engine.api;

import com.jblazing.model.Move;

/**
 * Asks the engine to play a move, sent to {@link EngineProtocol#MOVES_QUEUE}; it answers with a {@link MoveReply}.
 *
 * @param boardState the position to play from, as FEN
 * @param move       the player's move; null for the engine to choose one (the computer's move)
 */
public record MoveRequest(String boardState, Move move) {
}
