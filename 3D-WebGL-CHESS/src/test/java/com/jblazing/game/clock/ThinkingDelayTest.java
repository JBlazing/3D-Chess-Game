package com.jblazing.game.clock;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The computer thinks 5-10 seconds with more than 30 seconds on its clock, 1-3 seconds with less. */
class ThinkingDelayTest {

    private final RandomGenerator random = RandomGenerator.of("L64X128MixRandom");

    private List<Long> delays(Long msLeft) {
        return LongStream.range(0, 2_000).mapToObj(i -> ComputerOpponent.thinkingDelay(msLeft, random).toMillis()).toList();
    }

    @Test
    void withPlentyOfTimeItTakesFiveToTenSeconds() {
        List<Long> delays = delays(120_000L);
        assertThat(delays).allMatch(d -> d >= 5_000 && d <= 10_000);
        // spread over the whole range, not stuck at one end
        assertThat(Collections.min(delays)).isLessThan(5_500);
        assertThat(Collections.max(delays)).isGreaterThan(9_500);
    }

    @Test
    void underThirtySecondsItHurries() {
        assertThat(delays(29_000L)).allMatch(d -> d >= 1_000 && d <= 3_000);
        assertThat(delays(30_000L)).as("exactly 30 is not more than 30").allMatch(d -> d >= 1_000 && d <= 3_000);
        assertThat(delays(30_001L)).allMatch(d -> d >= 5_000);
    }

    @Test
    void neverThinksItselfOutOfTime() {
        assertThat(delays(2_000L)).allMatch(d -> d <= 1_000);
    }

    @Test
    void untimedGamesGetTheRelaxedDelay() {
        assertThat(delays(null)).allMatch(d -> d >= 5_000 && d <= 10_000);
    }
}
