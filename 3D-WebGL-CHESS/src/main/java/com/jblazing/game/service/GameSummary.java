package com.jblazing.game.service;

import com.jblazing.game.persistence.GameRecord;
import com.jblazing.player.Player;

import java.time.Instant;

/**
 * One of a player's games, from their side of the board.
 *
 * @param color    the side the player played, WHITE or BLACK
 * @param opponent the other side's player key, e.g. COMPUTER
 * @param winner   WHITE or BLACK after a checkmate, otherwise null
 * @param fen      the latest position
 */
public record GameSummary(String gameId, String color, String opponent, String status, String winner,
                          int moveCount, String moves, String fen, Instant createdAt, Instant updatedAt) {

    static GameSummary of(GameRecord record, Player player) {
        boolean white = record.getWhitePlayer().equals(player.id());
        String moves = record.getMoves();
        return new GameSummary(record.getId(), white ? "WHITE" : "BLACK",
                white ? record.getBlackPlayer() : record.getWhitePlayer(),
                record.getStatus(), record.getWinner(),
                moves.isEmpty() ? 0 : moves.split(" ").length, moves, record.getFen(),
                record.getCreatedAt(), record.getUpdatedAt());
    }
}
