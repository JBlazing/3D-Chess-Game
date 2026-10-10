package com.jblazing.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.Map;

/**
 * @param status the game's status after this move: IN_PROGRESS, CHECKMATE, STALEMATE or DRAW
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record Result(Move move, Map moveResult, String boardState, Map moveHistory, String status) {
}
