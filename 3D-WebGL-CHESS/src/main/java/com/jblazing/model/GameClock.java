package com.jblazing.model;

import java.time.Duration;
import java.time.Instant;

/**
 * A game's chess clock, kept by the server. Only the side to move has a running clock: the times
 * here are what each side had left at {@code turnStartedAt}, when the side to move began its turn,
 * and that side's time runs down from there.
 *
 * @param initialMs   time each side started with
 * @param incrementMs time added to a side's clock after each of its moves
 */
public record GameClock(long initialMs, long incrementMs, long whiteMs, long blackMs, Instant turnStartedAt) {

    public static GameClock start(TimeControl timeControl, Instant now) {
        long initial = timeControl.initial().toMillis();
        return new GameClock(initial, timeControl.increment().toMillis(), initial, initial, now);
    }

    /** Time the side has left now; never below zero. */
    public long remainingMs(boolean white, boolean whiteToMove, Instant now) {
        long stored = white ? whiteMs : blackMs;
        if (white != whiteToMove) {
            return stored;
        }
        return Math.max(0, stored - Duration.between(turnStartedAt, now).toMillis());
    }

    /** Whether the side to move has run out of time. */
    public boolean flagged(boolean whiteToMove, Instant now) {
        return remainingMs(whiteToMove, whiteToMove, now) == 0;
    }

    /** When the side to move runs out of time if it doesn't move first. */
    public Instant deadline(boolean whiteToMove) {
        return turnStartedAt.plusMillis(whiteToMove ? whiteMs : blackMs);
    }

    /** The clock once the side to move has moved: its thinking time is spent, the increment added, and the other side's clock starts. */
    public GameClock afterMove(boolean whiteMoved, Instant now) {
        long left = remainingMs(whiteMoved, whiteMoved, now) + incrementMs;
        return new GameClock(initialMs, incrementMs, whiteMoved ? left : whiteMs, whiteMoved ? blackMs : left, now);
    }

    /** The clock stopped where it is, e.g. when the game ends: nobody's time runs any more. */
    public GameClock stopped(boolean whiteToMove, Instant now) {
        long left = remainingMs(whiteToMove, whiteToMove, now);
        return new GameClock(initialMs, incrementMs, whiteToMove ? left : whiteMs, whiteToMove ? blackMs : left, now);
    }
}
