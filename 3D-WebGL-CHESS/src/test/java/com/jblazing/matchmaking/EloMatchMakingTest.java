package com.jblazing.matchmaking;

import com.jblazing.game.service.GameService;
import com.jblazing.player.Player;
import com.jblazing.user.UserAccount;
import com.jblazing.user.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Rating-based pairing, with a clock the test moves forward instead of waiting. */
class EloMatchMakingTest {

    /** A clock that only moves when told to. */
    static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T12:00:00Z");

        void advance(Duration d) { now = now.plus(d); }
        void advanceSeconds(long s) { advance(Duration.ofSeconds(s)); }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private final TestClock clock = new TestClock();
    private final GameService games = mock(GameService.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final Map<Long, UserAccount> byId = new HashMap<>();
    private EloMatchMaking matchmaking;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        when(games.createNewGame(any(), any())).thenAnswer(call -> UUID.randomUUID());
        when(accounts.findById(any())).thenAnswer(call -> Optional.ofNullable(byId.get((Long) call.getArgument(0))));
        matchmaking = new EloMatchMaking(games, accounts, clock);
    }

    private Player player(String name, int rating) {
        UserAccount account = new UserAccount("google", name, Instant.EPOCH);
        long id = nextId++;
        account.setId(id);
        account.setName("Full name of " + name);
        account.setUsername(name);
        account.setRating(rating);
        byId.put(id, account);
        return Player.account(id);
    }

    /** One pairing pass, as the scheduler runs every second; players keep their place by polling. */
    private void tick(Player... polling) {
        for (Player p : polling) matchmaking.status(p);
        matchmaking.createGames();
    }

    private String state(Player p) {
        return matchmaking.status(p).state();
    }

    @Test
    void closeRatingsArePairedStraightAway() {
        Player alice = player("alice", 1200), dave = player("dave", 1250);
        matchmaking.joinQueue(alice);
        matchmaking.joinQueue(dave);

        tick();

        QueueStatus a = matchmaking.status(alice), d = matchmaking.status(dave);
        assertThat(a.state()).isEqualTo("MATCHED");
        assertThat(d.state()).isEqualTo("MATCHED");
        assertThat(a.gameId()).isEqualTo(d.gameId());
        assertThat(a.color()).isNotEqualTo(d.color());
        assertThat(a.opponentName()).isEqualTo("dave");
        assertThat(a.opponentRating()).isEqualTo(1250);
        assertThat(d.opponentRating()).isEqualTo(1200);
        verify(games).createNewGame(any(), any());
        assertThat(matchmaking.getQueueSize()).isZero();
    }

    @Test
    void theWindowWidensWhileWaiting() {
        // 250 apart: needs both windows at 250, i.e. 100 + 15 * 10 seconds
        Player erin = player("erin", 1450), frank = player("frank", 1200);
        matchmaking.joinQueue(erin);
        matchmaking.joinQueue(frank);

        for (int second = 0; second < 10; second++) {
            tick(erin, frank);
            assertThat(state(erin)).as("after %ss", second).isEqualTo("SEARCHING");
            clock.advanceSeconds(1);
        }
        assertThat(matchmaking.status(erin).range()).isEqualTo(250);
        tick(erin, frank);

        assertThat(state(erin)).isEqualTo("MATCHED");
        assertThat(matchmaking.status(erin).gameId()).isEqualTo(matchmaking.status(frank).gameId());
    }

    @Test
    void theWindowStopsGrowingAt500() {
        Player carol = player("carol", 1800), bob = player("bob", 1200);
        matchmaking.joinQueue(carol);
        matchmaking.joinQueue(bob);

        for (int i = 0; i < 120; i++) {   // ten minutes
            clock.advanceSeconds(5);
            tick(carol, bob);
        }

        QueueStatus status = matchmaking.status(carol);
        assertThat(status.state()).isEqualTo("SEARCHING");
        assertThat(status.range()).isEqualTo(500);
        verify(games, never()).createNewGame(any(), any());
    }

    @Test
    void bothPlayersWindowsMustReachEachOther() {
        // a long wait gives the old player a wide window, but the newcomer's own window still has to reach
        Player veteran = player("veteran", 1200);
        matchmaking.joinQueue(veteran);
        clock.advanceSeconds(20);   // veteran's window: 100 + 300 = 400
        tick(veteran);
        Player newcomer = player("newcomer", 1500);
        matchmaking.joinQueue(newcomer);

        tick(veteran, newcomer);
        assertThat(state(newcomer)).as("newcomer's window is only 100").isEqualTo("SEARCHING");

        clock.advanceSeconds(13);   // newcomer: 100 + 195 = 295, still short of 300
        tick(veteran, newcomer);
        assertThat(state(newcomer)).isEqualTo("SEARCHING");

        clock.advanceSeconds(1);    // 310
        tick(veteran, newcomer);
        assertThat(state(newcomer)).isEqualTo("MATCHED");
    }

    @Test
    void theClosestRatingIsChosen() {
        Player alice = player("alice", 1200), far = player("far", 1290), near = player("near", 1210);
        matchmaking.joinQueue(alice);
        matchmaking.joinQueue(far);
        matchmaking.joinQueue(near);

        tick();

        assertThat(matchmaking.status(alice).opponentName()).isEqualTo("near");
        assertThat(state(far)).isEqualTo("SEARCHING");
    }

    @Test
    void signedInPlayersNeedAUsernameToQueue() {
        Player nameless = player("nameless", 1200);
        byId.get(nameless.accountId()).setUsername(null);

        assertThatThrownBy(() -> matchmaking.joinQueue(nameless))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409);
        assertThat(matchmaking.getQueueSize()).isZero();
    }

    @Test
    void opponentsSeeTheUsernameNotTheFullName() {
        Player alice = player("alice", 1200), bob = player("bob", 1200);
        matchmaking.joinQueue(alice);
        matchmaking.joinQueue(bob);

        tick();

        assertThat(matchmaking.status(alice).opponentName()).isEqualTo("bob");
        assertThat(matchmaking.status(bob).opponentName()).isEqualTo("alice");
    }

    @Test
    void signedInPlayersAreNeverPairedWithGuests() {
        Player alice = player("alice", 1200);
        Player guest = Player.guest(UUID.randomUUID().toString());
        matchmaking.joinQueue(alice);
        matchmaking.joinQueue(guest);

        clock.advanceSeconds(60);
        tick(alice, guest);

        assertThat(state(alice)).isEqualTo("SEARCHING");
        assertThat(state(guest)).isEqualTo("SEARCHING");
        assertThat(matchmaking.status(guest).rating()).as("guests are unrated").isNull();
    }

    @Test
    void guestsArePairedWithEachOther() {
        Player one = Player.guest("one"), two = Player.guest("two");
        matchmaking.joinQueue(one);
        matchmaking.joinQueue(two);

        tick();

        assertThat(state(one)).isEqualTo("MATCHED");
        assertThat(matchmaking.status(one).opponentRating()).isNull();
    }

    @Test
    void playersWhoStopPollingAreDropped() {
        Player gone = player("gone", 1200);
        matchmaking.joinQueue(gone);

        clock.advanceSeconds(16);
        matchmaking.createGames();   // no status() from them for 16 seconds

        assertThat(state(gone)).isEqualTo("IDLE");
        assertThat(matchmaking.getQueueSize()).isZero();
    }

    @Test
    void pollingKeepsYourPlaceAndYourWaitingTime() {
        Player alice = player("alice", 1200);
        matchmaking.joinQueue(alice);
        for (int i = 0; i < 6; i++) {
            clock.advanceSeconds(10);
            tick(alice);
        }

        QueueStatus status = matchmaking.status(alice);
        assertThat(status.state()).isEqualTo("SEARCHING");
        assertThat(status.waitedSeconds()).isEqualTo(60);
        matchmaking.joinQueue(alice);   // joining again doesn't restart the wait
        assertThat(matchmaking.status(alice).waitedSeconds()).isEqualTo(60);
    }

    @Test
    void leavingTheQueue() {
        Player alice = player("alice", 1200);
        matchmaking.joinQueue(alice);

        assertThat(matchmaking.leaveQueue(alice)).isTrue();
        assertThat(state(alice)).isEqualTo("IDLE");
        assertThat(matchmaking.leaveQueue(alice)).isFalse();
    }
}
