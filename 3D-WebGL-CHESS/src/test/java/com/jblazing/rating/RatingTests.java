package com.jblazing.rating;

import com.jblazing.game.service.LiveGame;
import com.jblazing.model.Move;
import com.jblazing.player.Player;
import com.jblazing.support.TestSupport;
import com.jblazing.user.UserAccount;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Elo ratings: rated games between signed-in players change both ratings when they end, however
 * they end; games against the computer never do.
 */
interface RatingTests extends TestSupport {

    /** Expected change, computed here independently: K = 40 for the first 30 games, 10 from 2400, else 20. */
    static int eloChange(int rating, int ratedGames, int opponent, double score) {
        int k = ratedGames < 30 ? 40 : rating >= 2400 ? 10 : 20;
        return (int) Math.round(k * (score - 1 / (1 + Math.pow(10, (opponent - rating) / 400.0))));
    }

    @Test
    default void onlyGamesBetweenSignedInPlayersAreRated() {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");

        assertThat(games().liveGame(games().createNewGame(alice, bob), alice).rated()).isTrue();
        assertThat(games().liveGame(games().createNewGame(alice, Player.COMPUTER), alice).rated()).isFalse();
    }

    @Test
    default void checkmateChangesBothRatingsByTheEloFormula() {
        Player white = newPlayer("white", 1250), black = newPlayer("black", 1200);
        UUID gameId = games().createNewGame(white, black);

        // Fool's Mate: black wins
        games().playerMove(gameId, white, new Move("f2", "f3"));
        games().playerMove(gameId, black, new Move("e7", "e5"));
        games().playerMove(gameId, white, new Move("g2", "g4"));
        games().playerMove(gameId, black, new Move("d8", "h4"));

        assertThat(account(white).getRating()).isEqualTo(1250 + eloChange(1250, 0, 1200, 0));
        assertThat(account(black).getRating()).isEqualTo(1200 + eloChange(1200, 0, 1250, 1));
        assertThat(account(white).getRatedGames()).isEqualTo(1);
        LiveGame result = games().liveGame(gameId, black);
        assertThat(result.status()).isEqualTo("CHECKMATE");
        assertThat(result.winner()).isEqualTo("BLACK");
        assertThat(result.ratingChange()).isEqualTo(eloChange(1200, 0, 1250, 1));
    }

    @Test
    default void aResultIsAppliedOnlyOnce() {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        UUID gameId = games().createNewGame(alice, bob);
        games().resign(gameId, alice);
        int rating = account(alice).getRating();

        ratings().applyResult(gameId.toString());
        ratings().applyResult(gameId.toString());

        assertThat(account(alice).getRating()).isEqualTo(rating);
        assertThat(account(alice).getRatedGames()).isEqualTo(1);
    }

    @Test
    default void resigningLosesToTheOpponent() {
        Player alice = newPlayer("alice", 1300), bob = newPlayer("bob", 1280);
        UUID gameId = games().createNewGame(alice, bob);

        games().resign(gameId, alice);

        assertThat(account(alice).getRating()).isEqualTo(1300 + eloChange(1300, 0, 1280, 0));
        assertThat(account(bob).getRating()).isEqualTo(1280 + eloChange(1280, 0, 1300, 1));
        assertThat(games().liveGame(gameId, bob).status()).isEqualTo("RESIGNED");
        assertThat(games().liveGame(gameId, bob).winner()).isEqualTo("BLACK");
    }

    @Test
    default void stalemateScoresADraw() {
        Player alice = newPlayer("alice", 1200), bob = newPlayer("bob", 1260);
        UUID gameId = games().createNewGame(alice, bob);
        sql("update game set fen = 'k7/8/1K6/2Q5/8/8/8/8 w - - 0 60' where id = '" + gameId + "'");
        cache().removeGameState(gameId);

        games().playerMove(gameId, alice, new Move("c5", "c7"));

        assertThat(games().liveGame(gameId, alice).status()).isEqualTo("STALEMATE");
        assertThat(account(alice).getRating()).isEqualTo(1200 + eloChange(1200, 0, 1260, 0.5));
        assertThat(account(bob).getRating()).isEqualTo(1260 + eloChange(1260, 0, 1200, 0.5));
    }

    @Test
    default void gamesAgainstTheComputerNeverChangeRatings() {
        Player alice = newPlayer("alice");

        games().resign(games().createNewGame(alice, Player.COMPUTER), alice);   // a loss
        UUID win = games().createNewGame(alice, Player.COMPUTER);
        sql("update game set fen = '6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1' where id = '" + win + "'");
        cache().removeGameState(win);
        games().playerMove(win, alice, new Move("d1", "d8"));                 // a win by checkmate

        LiveGame result = games().liveGame(win, alice);
        assertThat(result.status()).isEqualTo("CHECKMATE");
        assertThat(result.rated()).isFalse();
        assertThat(result.ratingChange()).isNull();
        assertThat(account(alice).getRating()).isEqualTo(1200);
        assertThat(account(alice).getRatedGames()).isZero();
    }

    @Test
    default void anAbandonedGameCanBeClaimedAfterTwoMinutes() {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        // untimed: on a clock, an absent player's time runs out instead (see GameClockTests)
        UUID gameId = games().createNewGame(alice, bob, null);
        games().playerMove(gameId, alice, new Move("e2", "e4"));   // bob to move

        assertThat(statusOf(() -> games().claimWin(gameId, alice))).as("opponent still has time").isEqualTo(409);
        assertThat(statusOf(() -> games().claimWin(gameId, bob))).as("can't claim on your own move").isEqualTo(409);

        sql("update game set updated_at = " + minutesAgo(3) + " where id = '" + gameId + "'");
        LiveGame idle = games().liveGame(gameId, alice);
        assertThat(idle.canClaimWin()).isTrue();
        assertThat(idle.opponentIdleSeconds()).isGreaterThanOrEqualTo(170);

        games().claimWin(gameId, alice);

        LiveGame claimed = games().liveGame(gameId, alice);
        assertThat(claimed.status()).isEqualTo("ABANDONED");
        assertThat(claimed.winner()).isEqualTo("WHITE");
        assertThat(claimed.ratingChange()).isPositive();
        assertThat(account(alice).getRating()).isEqualTo(1200 + claimed.ratingChange());
    }

    @Test
    default void twoGamesEndingAtOnceBothCount() throws Exception {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        UUID first = games().createNewGame(alice, bob), second = games().createNewGame(bob, alice);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            Future<?> a = pool.submit(() -> { go.await(); games().resign(first, bob); return null; });
            Future<?> b = pool.submit(() -> { go.await(); games().resign(second, bob); return null; });
            go.countDown();
            a.get();
            b.get();
        } finally {
            pool.shutdownNow();
        }

        int gained = games().liveGame(first, alice).ratingChange() + games().liveGame(second, alice).ratingChange();
        assertThat(account(alice).getRating()).as("no lost update").isEqualTo(1200 + gained);
        assertThat(account(alice).getRatedGames()).isEqualTo(2);
    }

    @Test
    default void kFactorDropsAfterThirtyGamesAndAgainFrom2400() {
        Player carol = newPlayer("carol", 1800), erin = newPlayer("erin", 2450);
        UserAccount c = account(carol);
        c.setRatedGames(30);
        accounts().save(c);
        UserAccount e = account(erin);
        e.setRatedGames(30);
        accounts().save(e);
        UUID gameId = games().createNewGame(carol, erin);

        games().resign(gameId, erin);

        // carol (30 games, under 2400): K = 20; erin (30 games, 2400+): K = 10
        assertThat(account(carol).getRating() - 1800).isEqualTo(eloChange(1800, 30, 2450, 1));
        assertThat(account(erin).getRating() - 2450).isEqualTo(eloChange(2450, 30, 1800, 0));
    }

    @Test
    default void theActiveOnlineGameIsFoundForReloads() {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        UUID gameId = games().createNewGame(bob, alice);
        games().createNewGame(alice, Player.COMPUTER);   // computer games don't count as online

        assertThat(games().activeOnlineGame(alice)).map(LiveGame::gameId).contains(gameId.toString());
        assertThat(games().activeOnlineGame(bob)).map(LiveGame::gameId).contains(gameId.toString());

        games().resign(gameId, bob);
        assertThat(games().activeOnlineGame(alice)).isEmpty();
    }
}
