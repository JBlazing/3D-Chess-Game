package com.jblazing.rating;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The Elo arithmetic on its own. */
class RatingServiceTest {

    @Test
    void equalRatingsExpectHalfAPoint() {
        assertThat(RatingService.expectedScore(1500, 1500)).isEqualTo(0.5);
    }

    @Test
    void a400PointGapIsTenToOne() {
        assertThat(RatingService.expectedScore(1600, 1200)).isCloseTo(10.0 / 11, within(1e-9));
        assertThat(RatingService.expectedScore(1200, 1600)).isCloseTo(1.0 / 11, within(1e-9));
    }

    @Test
    void expectedScoresAddUpToOne() {
        assertThat(RatingService.expectedScore(1337, 1888) + RatingService.expectedScore(1888, 1337)).isCloseTo(1.0, within(1e-12));
    }

    @Test
    void kFactorTiers() {
        assertThat(RatingService.kFactor(1200, 0)).isEqualTo(40);
        assertThat(RatingService.kFactor(2600, 29)).as("still provisional").isEqualTo(40);
        assertThat(RatingService.kFactor(1200, 30)).isEqualTo(20);
        assertThat(RatingService.kFactor(2399, 100)).isEqualTo(20);
        assertThat(RatingService.kFactor(2400, 100)).isEqualTo(10);
    }

    @Test
    void changesBetweenEqualPlayers() {
        assertThat(RatingService.change(1200, 0, 1200, 1)).isEqualTo(20);
        assertThat(RatingService.change(1200, 0, 1200, 0)).isEqualTo(-20);
        assertThat(RatingService.change(1200, 0, 1200, 0.5)).isZero();
        assertThat(RatingService.change(1500, 50, 1500, 1)).isEqualTo(10);
    }

    @Test
    void beatingAStrongerPlayerGainsMore() {
        assertThat(RatingService.change(1200, 0, 1600, 1)).isEqualTo(36);   // 40 * (1 - 1/11)
        assertThat(RatingService.change(1600, 0, 1200, 1)).isEqualTo(4);    // 40 * (1 - 10/11)
        assertThat(RatingService.change(1200, 0, 1600, 0.5)).isPositive();  // a draw against a stronger player still gains
    }
}
