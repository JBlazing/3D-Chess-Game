package com.jblazing.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock arithmetic: only the side to move loses time, each move adds the increment. */
class GameClockTest {

    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");
    private static final GameClock BLITZ = GameClock.start(new TimeControl(Duration.ofMinutes(5), Duration.ofSeconds(3)), START);

    @Test
    void onlyTheSideToMoveRunsDown() {
        Instant later = START.plusSeconds(20);

        assertThat(BLITZ.remainingMs(true, true, later)).isEqualTo(280_000);
        assertThat(BLITZ.remainingMs(false, true, later)).isEqualTo(300_000);
    }

    @Test
    void aMoveSpendsTheThinkingTimeAndAddsTheIncrement() {
        GameClock afterWhite = BLITZ.afterMove(true, START.plusSeconds(20));

        assertThat(afterWhite.whiteMs()).isEqualTo(283_000);
        assertThat(afterWhite.blackMs()).isEqualTo(300_000);
        assertThat(afterWhite.turnStartedAt()).isEqualTo(START.plusSeconds(20));
        // now black's clock runs, white's is frozen
        assertThat(afterWhite.remainingMs(false, false, START.plusSeconds(30))).isEqualTo(290_000);
        assertThat(afterWhite.remainingMs(true, false, START.plusSeconds(30))).isEqualTo(283_000);
    }

    @Test
    void theFlagFallsAtTheDeadline() {
        Instant deadline = BLITZ.deadline(true);

        assertThat(deadline).isEqualTo(START.plus(Duration.ofMinutes(5)));
        assertThat(BLITZ.flagged(true, deadline.minusMillis(1))).isFalse();
        assertThat(BLITZ.flagged(true, deadline)).isTrue();
        assertThat(BLITZ.remainingMs(true, true, deadline.plusSeconds(10))).as("never negative").isZero();
    }

    @Test
    void aStoppedClockKeepsWhatWasLeft() {
        GameClock stopped = BLITZ.stopped(true, START.plusSeconds(42));

        assertThat(stopped.whiteMs()).isEqualTo(258_000);
        assertThat(stopped.blackMs()).isEqualTo(300_000);
    }

    @Test
    void timeControlLabels() {
        assertThat(TimeControl.label(300_000, 3_000)).isEqualTo("5+3");
        assertThat(TimeControl.label(180_000, 0)).isEqualTo("3+0");
        assertThat(TimeControl.label(90_000, 1_000)).isEqualTo("90s+1");
    }
}
