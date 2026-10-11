package com.jblazing.game.service;

import com.jblazing.game.persistence.GameRecord;
import com.jblazing.model.TimeControl;
import com.jblazing.player.Player;
import com.jblazing.user.UserAccount;

import java.time.Instant;

/**
 * One of a player's games, from their side of the board.
 *
 * @param color          the side the player played, WHITE or BLACK
 * @param opponent       the other side's player key, e.g. COMPUTER
 * @param opponentName   who that was: "Computer", the opponent's name, or "Guest"
 * @param winner         WHITE or BLACK when the game has one, otherwise null
 * @param fen            the latest position
 * @param rating         the player's rating when the game started; null when unrated
 * @param opponentRating the opponent's rating when the game started; null when unrated
 * @param ratingChange   how much the player's rating changed; null when unrated or unfinished
 * @param timeControl    the blitz clock it was played on, e.g. "5+3"; null for untimed games
 */
public record GameSummary(String gameId, String color, String opponent, String opponentName, String status, String winner,
                          int moveCount, String moves, String fen, boolean rated, Integer rating, Integer opponentRating,
                          Integer ratingChange, Instant createdAt, Instant updatedAt, String timeControl) {

    /** Must be called inside a transaction: it reads the opponent's username. */
    static GameSummary of(GameRecord record, Player player) {
        boolean white = record.getWhitePlayer().equals(player.id());
        String opponent = white ? record.getBlackPlayer() : record.getWhitePlayer();
        UserAccount opponentAccount = white ? record.getBlackAccount() : record.getWhiteAccount();
        String opponentName = Player.COMPUTER.id().equals(opponent) ? "Computer"
                : opponentAccount != null ? opponentAccount.displayName()
                : "Guest";
        String moves = record.getMoves();
        return new GameSummary(record.getId(), white ? "WHITE" : "BLACK", opponent, opponentName,
                record.getStatus(), record.getWinner(),
                moves.isEmpty() ? 0 : moves.split(" ").length, moves, record.getFen(), record.isRated(),
                white ? record.getWhiteRating() : record.getBlackRating(),
                white ? record.getBlackRating() : record.getWhiteRating(),
                white ? record.getWhiteRatingChange() : record.getBlackRatingChange(),
                record.getCreatedAt(), record.getUpdatedAt(),
                record.getClockInitialMs() == null ? null : TimeControl.label(record.getClockInitialMs(), record.getClockIncrementMs()));
    }
}
