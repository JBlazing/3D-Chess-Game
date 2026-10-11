package com.jblazing.game;

import com.jblazing.game.service.LiveGame;
import com.jblazing.model.Move;
import com.jblazing.model.TimeControl;
import com.jblazing.player.Player;
import com.jblazing.support.TestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Online games are blitz (5+3): the server keeps the clock, charges each move to the mover, and
 * ends the game when a flag falls, whether or not anyone is connected.
 */
interface GameClockTests extends TestSupport {

    @Test
    default void onlineAndComputerGamesAreBlitz() {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");

        LiveGame online = games().liveGame(games().createNewGame(alice, bob), alice);
        assertThat(online.clock().timeControl()).isEqualTo("5+3");
        assertThat(online.clock().running()).isEqualTo("WHITE");
        assertThat(online.clock().whiteMs()).isBetween(295_000L, 300_000L);
        assertThat(online.clock().blackMs()).isEqualTo(300_000L);

        UUID computerGame = gameController().startGame("WHITE", alice).gameId();
        assertThat(games().liveGame(computerGame, alice).clock().timeControl()).isEqualTo("5+3");
        assertThat(games().summaryFor(computerGame, alice).timeControl()).isEqualTo("5+3");
    }

    @Test
    default void theComputersReplyRunsItsOwnClock() {
        Player alice = newPlayer("alice");
        UUID gameId = gameController().startGame("WHITE", alice).gameId();
        sql("update game set updated_at = " + minutesAgo(1) + " where id = '" + gameId + "'");
        cache().removeGameState(gameId);

        gameController().move(gameId, new Move("e2", "e4"), alice);
        games().playComputerMove(gameId).orElseThrow();

        LiveGame.Clock clock = games().liveGame(gameId, alice).clock();
        assertThat(clock.whiteMs()).isBetween(242_000L, 243_100L);
        assertThat(clock.blackMs()).as("thinking time spent, increment added").isBetween(290_000L, 303_000L);
        assertThat(clock.running()).isEqualTo("WHITE");
    }

    @Test
    default void theComputerMovesByItselfAfterThinkingFiveToTenSeconds() throws InterruptedException {
        Player alice = newPlayer("alice");
        // the computer has white and plenty of time: nobody asks it to move, the server plays it
        UUID gameId = gameController().startGame("BLACK", alice).gameId();
        long started = System.currentTimeMillis();

        Thread.sleep(4_000);
        assertThat(games().liveGame(gameId, alice).moves()).as("still thinking").isEmpty();
        while (games().liveGame(gameId, alice).moves().isEmpty() && System.currentTimeMillis() < started + 15_000) {
            Thread.sleep(100);
        }

        LiveGame game = games().liveGame(gameId, alice);
        assertThat(game.moves()).hasSize(1);
        assertThat(game.status()).isEqualTo("IN_PROGRESS");
        assertThat(game.clock().running()).isEqualTo("BLACK");
        // its clock paid for the thinking: 5:00 - (5 to 10 seconds, plus the engine) + 0:03
        assertThat(game.clock().whiteMs()).isBetween(300_000L - 12_000 + 3_000, 300_000L - 5_000 + 3_000);
        assertThat(games().playComputerMove(gameId)).as("already moved").isEmpty();
    }

    @Test
    default void aNewGameStopsTheOldComputerGame() {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        UUID online = games().createNewGame(alice, bob);
        UUID old = gameController().startGame("WHITE", alice).gameId();
        gameController().move(old, new Move("e2", "e4"), alice);

        UUID next = gameController().startGame("BLACK", alice).gameId();

        LiveGame stopped = games().liveGame(old, alice);
        assertThat(stopped.status()).isEqualTo("ABORTED");
        assertThat(stopped.winner()).isNull();
        assertThat(stopped.clock().running()).as("its clock stopped").isNull();
        assertThat(games().playComputerMove(old)).as("the computer doesn't move in it any more").isEmpty();
        assertThat(games().liveGame(next, alice).status()).isEqualTo("IN_PROGRESS");
        assertThat(games().liveGame(online, alice).status()).as("online games aren't touched").isEqualTo("IN_PROGRESS");
        assertThat(account(alice).getRating()).isEqualTo(1200);

        // an online game replaces a computer game too
        games().createNewGame(bob, alice);
        assertThat(games().liveGame(next, alice).status()).isEqualTo("ABORTED");
    }

    @Test
    default void runningOutAgainstTheComputerLosesUnrated() {
        Player alice = newPlayer("alice");
        UUID gameId = gameController().startGame("WHITE", alice).gameId();
        sql("update game set updated_at = " + minutesAgo(6) + " where id = '" + gameId + "'");
        cache().removeGameState(gameId);

        assertThat(statusOf(() -> gameController().move(gameId, new Move("e2", "e4"), alice))).isEqualTo(409);

        LiveGame result = games().liveGame(gameId, alice);
        assertThat(result.status()).isEqualTo("TIMEOUT");
        assertThat(result.winner()).isEqualTo("BLACK");
        assertThat(result.rated()).isFalse();
        assertThat(result.ratingChange()).isNull();
        assertThat(account(alice).getRating()).isEqualTo(1200);
    }

    @Test
    default void aMoveSpendsTheMoversTimeAndAddsTheIncrement() {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        UUID gameId = games().createNewGame(alice, bob);
        // white has been thinking for a minute; the clock is rebuilt from the database
        sql("update game set updated_at = " + minutesAgo(1) + " where id = '" + gameId + "'");
        cache().removeGameState(gameId);

        games().playerMove(gameId, alice, new Move("e2", "e4"));

        LiveGame.Clock clock = games().liveGame(gameId, bob).clock();
        // a little leeway either way: "a minute ago" comes from the database's clock, not the JVM's
        assertThat(clock.whiteMs()).as("5:00 - 1:00 + 0:03").isBetween(242_000L, 243_100L);
        assertThat(clock.blackMs()).isBetween(295_000L, 300_000L);
        assertThat(clock.running()).isEqualTo("BLACK");
    }

    @Test
    default void movingAfterTheFlagFellLosesOnTime() {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        UUID gameId = games().createNewGame(alice, bob);
        sql("update game set updated_at = " + minutesAgo(6) + " where id = '" + gameId + "'");
        cache().removeGameState(gameId);

        assertThat(statusOf(() -> games().playerMove(gameId, alice, new Move("e2", "e4")))).isEqualTo(409);

        LiveGame result = games().liveGame(gameId, bob);
        assertThat(result.status()).isEqualTo("TIMEOUT");
        assertThat(result.winner()).isEqualTo("BLACK");
        assertThat(result.moves()).isEmpty();
        assertThat(result.clock().whiteMs()).isZero();
        assertThat(result.clock().running()).as("stopped").isNull();
        assertThat(result.ratingChange()).as("rated like any other result").isPositive();
        assertThat(result.canClaimWin()).isFalse();
    }

    @Test
    default void theFlagFallsWithoutAnyoneMoving() throws InterruptedException {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        UUID gameId = games().createNewGame(alice, bob, new TimeControl(Duration.ofMillis(400), Duration.ZERO));

        long until = System.currentTimeMillis() + 5_000;
        while (games().liveGame(gameId, alice).status().equals("IN_PROGRESS") && System.currentTimeMillis() < until) {
            Thread.sleep(50);
        }

        LiveGame result = games().liveGame(gameId, alice);
        assertThat(result.status()).isEqualTo("TIMEOUT");
        assertThat(result.winner()).isEqualTo("BLACK");
        assertThat(account(bob).getRatedGames()).isEqualTo(1);
    }

    @Test
    default void theClockKeepsRunningWhileTheOpponentThinks() throws InterruptedException {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        UUID gameId = games().createNewGame(alice, bob, new TimeControl(Duration.ofSeconds(60), Duration.ofSeconds(1)));
        games().playerMove(gameId, alice, new Move("e2", "e4"));
        long before = games().liveGame(gameId, alice).clock().blackMs();

        Thread.sleep(300);

        LiveGame.Clock clock = games().liveGame(gameId, alice).clock();
        assertThat(before - clock.blackMs()).isBetween(250L, 2_000L);
        assertThat(clock.whiteMs()).as("white's clock is frozen").isGreaterThan(60_000L);
    }

    @Test
    default void runningOutAgainstALoneKingIsADraw() {
        Player alice = newPlayer("alice", 1200), bob = newPlayer("bob", 1300);
        UUID gameId = games().createNewGame(alice, bob);
        // black to move with a queen; white has only a king and could never checkmate
        sql("update game set fen = 'k6q/8/8/8/8/8/8/K7 b - - 0 60', updated_at = " + minutesAgo(6) + " where id = '" + gameId + "'");
        cache().removeGameState(gameId);

        assertThat(games().flagIfOutOfTime(gameId)).isTrue();

        LiveGame result = games().liveGame(gameId, alice);
        assertThat(result.status()).isEqualTo("TIMEOUT");
        assertThat(result.winner()).isNull();
        assertThat(result.ratingChange()).as("a draw against a higher rating gains").isPositive();
    }

    @Test
    default void aClockWithTimeLeftIsLeftAlone() {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        UUID gameId = games().createNewGame(alice, bob);

        assertThat(games().flagIfOutOfTime(gameId)).isFalse();
        assertThat(games().liveGame(gameId, alice).status()).isEqualTo("IN_PROGRESS");
    }
}
