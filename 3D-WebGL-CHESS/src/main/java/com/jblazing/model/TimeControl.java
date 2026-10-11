package com.jblazing.model;

import java.time.Duration;

/**
 * How much time each side gets: {@code initial} on the clock to start with, plus {@code increment}
 * added after every move they make. Blitz 5+3, for example, is five minutes plus three seconds a move.
 */
public record TimeControl(Duration initial, Duration increment) {

    /** "5+3": minutes on the clock, then seconds added per move ("90s+0" when not whole minutes). */
    public static String label(long initialMs, long incrementMs) {
        long seconds = initialMs / 1000;
        return (seconds % 60 == 0 ? String.valueOf(seconds / 60) : seconds + "s") + "+" + incrementMs / 1000;
    }
}
