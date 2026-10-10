package com.jblazing.model;

import com.jblazing.player.Player;

import java.util.UUID;

/**
 * A game in play.
 *
 * @param boardState current position as FEN; its side-to-move field says whose turn it is
 * @param status     IN_PROGRESS, CHECKMATE, STALEMATE or DRAW
 */
public record GameState(UUID gameId,
                        Player white,
                        Player black,
                        String boardState,
                        String status) {

    public static final String STARTING_POSITION = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    public static final String IN_PROGRESS = "IN_PROGRESS";

    public boolean isOver() {
        return !IN_PROGRESS.equals(status);
    }

    public Player playerToMove() {
        return boardState.split(" ")[1].equals("w") ? white : black;
    }

    public boolean hasPlayer(Player player) {
        return white.id().equals(player.id()) || black.id().equals(player.id());
    }
}
