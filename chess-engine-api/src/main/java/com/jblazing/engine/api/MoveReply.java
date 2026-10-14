package com.jblazing.engine.api;

import com.jblazing.model.Result;

/**
 * The engine's answer to a {@link MoveRequest}.
 *
 * @param outcome whether the move was played
 * @param result  the move played and the position after it; only when {@code PLAYED}
 * @param error   what went wrong; only when {@code FAILED}
 */
public record MoveReply(Outcome outcome, Result result, String error) {

    public enum Outcome {
        /** The move was played. */
        PLAYED,
        /** The move isn't legal in the position, or the computer has no move (the game is over). */
        ILLEGAL,
        /** The engine couldn't work it out; nothing is wrong with the move, so it can be tried again. */
        FAILED
    }

    public static MoveReply played(Result result) {
        return new MoveReply(Outcome.PLAYED, result, null);
    }

    public static MoveReply illegal() {
        return new MoveReply(Outcome.ILLEGAL, null, null);
    }

    public static MoveReply failed(String error) {
        return new MoveReply(Outcome.FAILED, null, error);
    }
}
