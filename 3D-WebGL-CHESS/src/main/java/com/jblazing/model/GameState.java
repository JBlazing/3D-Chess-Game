package com.jblazing.model;

import com.jblazing.player.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A game: everything needed to play it, which is all that is kept while it is in progress (see
 * {@code GameStateCache}). The database only gets the game once it is over.
 *
 * @param boardState  current position as FEN; its side-to-move field says whose turn it is
 * @param status      IN_PROGRESS, CHECKMATE, STALEMATE, DRAW, RESIGNED, ABANDONED, TIMEOUT or ABORTED
 * @param clock       the blitz clock; null for untimed games
 * @param winner      WHITE or BLACK once the game has one, otherwise null
 * @param moves       every move so far, in SAN
 * @param rated       rated games are between two signed-in players; never against the computer
 * @param whiteRating white's rating when the game started; null when white has no account
 * @param blackRating the same for black
 * @param updatedAt   when the game last changed: the last move, or the start
 */
public record GameState(UUID gameId,
                        Player white,
                        Player black,
                        String boardState,
                        String status,
                        GameClock clock,
                        String winner,
                        List<String> moves,
                        boolean rated,
                        Integer whiteRating,
                        Integer blackRating,
                        Instant createdAt,
                        Instant updatedAt) {

    public static final String STARTING_POSITION = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    public static final String IN_PROGRESS = "IN_PROGRESS";

    public GameState {
        moves = moves == null ? List.of() : List.copyOf(moves);
    }

    /** A new game, white to move. */
    public static GameState start(UUID gameId, Player white, Player black, GameClock clock, boolean rated,
                                  Integer whiteRating, Integer blackRating, Instant now) {
        return new GameState(gameId, white, black, STARTING_POSITION, IN_PROGRESS, clock, null, List.of(),
                rated, whiteRating, blackRating, now, now);
    }

    /** The game after a move, given in SAN, that left the position {@code fen}. */
    public GameState afterMove(String san, String fen, String status, String winner, GameClock clock, Instant now) {
        List<String> played = new ArrayList<>(moves);
        played.add(san);
        return new GameState(gameId, white, black, fen, status, clock, winner, played, rated, whiteRating, blackRating, createdAt, now);
    }

    /** The game ended without a move, e.g. by resignation. */
    public GameState ended(String status, String winner, GameClock clock, Instant now) {
        return new GameState(gameId, white, black, boardState, status, clock, winner, moves, rated, whiteRating, blackRating, createdAt, now);
    }

    public boolean isOver() {
        return !IN_PROGRESS.equals(status);
    }

    public boolean whiteToMove() {
        return boardState.split(" ")[1].equals("w");
    }

    public Player playerToMove() {
        return whiteToMove() ? white : black;
    }

    public boolean hasPlayer(Player player) {
        return white.id().equals(player.id()) || black.id().equals(player.id());
    }

    public boolean isTimed() {
        return clock != null;
    }

    /** Both sides are people, not the computer. */
    public boolean isOnline() {
        return !white.isComputer() && !black.isComputer();
    }
}
