package com.jblazing.game.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Running out of time loses, unless the opponent could never have checkmated: then it is a draw. */
class TimeoutWinnerTest {

    @Test
    void theOpponentWins() {
        assertThat(GameService.timeoutWinner("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")).isEqualTo("BLACK");
        assertThat(GameService.timeoutWinner("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")).isEqualTo("WHITE");
    }

    @Test
    void aLoneKingCantWinOnTime() {
        // black flags, white has only a king
        assertThat(GameService.timeoutWinner("k6q/8/8/8/8/8/8/K7 b - - 0 60")).isNull();
    }

    @Test
    void kingAndMinorPieceAgainstALoneKingCantWin() {
        assertThat(GameService.timeoutWinner("k7/8/8/8/8/8/8/K5N1 b - - 0 60")).isNull();
        assertThat(GameService.timeoutWinner("k7/8/8/8/8/8/8/K5B1 b - - 0 60")).isNull();
    }

    @Test
    void aMinorPieceWinsWhenTheFlaggedSideHasMaterial() {
        // a knight can mate a king hemmed in by its own pieces, so the flag loses
        assertThat(GameService.timeoutWinner("k6p/8/8/8/8/8/8/K5N1 b - - 0 60")).isEqualTo("WHITE");
        // a single pawn can still promote
        assertThat(GameService.timeoutWinner("k7/8/8/8/8/8/P7/K7 b - - 0 60")).isEqualTo("WHITE");
    }
}
