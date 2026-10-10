package com.jblazing.game;

import com.jblazing.game.service.GameSummary;
import com.jblazing.model.GameStart;
import com.jblazing.model.Move;
import com.jblazing.model.Result;
import com.jblazing.player.Player;
import com.jblazing.support.TestSupport;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Games belong to the signed-in player who started them, are saved as they are played, and enforce the rules. */
interface GameLinkingTests extends TestSupport {

    @Test
    default void aNewGameIsLinkedToThePlayersAccount() throws SQLException {
        Player alice = newPlayer("alice");

        UUID gameId = gameController().startGame("WHITE", alice).gameId();

        try (Connection c = dataSource().getConnection(); Statement s = c.createStatement();
             ResultSet row = s.executeQuery("select white_player, black_player, white_account_id, black_account_id, status, rated from game where id = '" + gameId + "'")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getString("white_player")).isEqualTo(alice.id());
            assertThat(row.getString("black_player")).isEqualTo("COMPUTER");
            assertThat(row.getLong("white_account_id")).isEqualTo(alice.accountId());
            assertThat(row.getObject("black_account_id")).isNull();
            assertThat(row.getString("status")).isEqualTo("IN_PROGRESS");
            assertThat(row.getBoolean("rated")).isFalse();
        }
    }

    @Test
    default void movesAreSavedAndTheGameReloadsFromTheDatabase() {
        Player alice = newPlayer("alice");
        UUID gameId = gameController().startGame("WHITE", alice).gameId();

        gameController().move(gameId, new Move("e2", "e4"), alice);
        gameController().opponentMove(gameId, alice);
        cache().removeGameState(gameId);   // as if it had expired from memory, or the server restarted
        Result third = gameController().move(gameId, new Move("d2", "d4"), alice);

        assertThat(third.moveResult().get("san")).isEqualTo("d4");
        GameSummary saved = gameController().game(gameId, alice);
        assertThat(saved.moveCount()).isEqualTo(3);
        assertThat(saved.moves()).startsWith("e4 ").endsWith(" d4");
    }

    @Test
    default void otherPlayersCannotSeeOrPlayTheGame() {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        UUID gameId = gameController().startGame("WHITE", alice).gameId();

        // someone else's game looks exactly like a game that doesn't exist
        assertThat(statusOf(() -> gameController().move(gameId, new Move("e2", "e4"), bob))).isEqualTo(404);
        assertThat(statusOf(() -> gameController().opponentMove(gameId, bob))).isEqualTo(404);
        assertThat(statusOf(() -> gameController().game(gameId, bob))).isEqualTo(404);
        assertThat(statusOf(() -> gameController().move(UUID.randomUUID(), new Move("e2", "e4"), alice))).isEqualTo(404);
    }

    @Test
    default void turnsAndLegalityAreEnforced() {
        Player alice = newPlayer("alice");
        UUID gameId = gameController().startGame("WHITE", alice).gameId();

        assertThat(statusOf(() -> gameController().opponentMove(gameId, alice))).as("computer on your turn").isEqualTo(409);
        assertThat(statusOf(() -> gameController().move(gameId, new Move("e2", "e5"), alice))).as("illegal move").isEqualTo(400);
        gameController().move(gameId, new Move("e2", "e4"), alice);
        assertThat(statusOf(() -> gameController().move(gameId, new Move("d2", "d4"), alice))).as("moving twice").isEqualTo(409);
        gameController().opponentMove(gameId, alice);
        assertThat(statusOf(() -> gameController().opponentMove(gameId, alice))).as("computer moving twice").isEqualTo(409);
    }

    @Test
    default void startingAsBlackLetsTheComputerOpen() {
        Player alice = newPlayer("alice");

        GameStart start = gameController().startGame("BLACK", alice);

        assertThat(start.startingMove()).isNotNull();
        GameSummary saved = gameController().game(start.gameId(), alice);
        assertThat(saved.color()).isEqualTo("BLACK");
        assertThat(saved.moveCount()).isEqualTo(1);
    }

    @Test
    default void checkmateEndsTheGame() {
        Player alice = newPlayer("alice");
        UUID gameId = gameController().startGame("WHITE", alice).gameId();
        // one move from mate: Rd8#
        sql("update game set fen = '6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1' where id = '" + gameId + "'");
        cache().removeGameState(gameId);

        Result mate = gameController().move(gameId, new Move("d1", "d8"), alice);

        assertThat(mate.moveResult().get("san")).isEqualTo("Rd8#");
        assertThat(mate.status()).isEqualTo("CHECKMATE");
        GameSummary saved = gameController().game(gameId, alice);
        assertThat(saved.status()).isEqualTo("CHECKMATE");
        assertThat(saved.winner()).isEqualTo("WHITE");
        assertThat(statusOf(() -> gameController().opponentMove(gameId, alice))).isEqualTo(409);
        assertThat(statusOf(() -> gameController().move(gameId, new Move("g1", "f1"), alice))).isEqualTo(409);
    }

    @Test
    default void myGamesListsOnlyThePlayersOwnGames() {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        UUID asWhite = gameController().startGame("WHITE", alice).gameId();
        UUID asBlack = gameController().startGame("BLACK", alice).gameId();

        List<GameSummary> mine = gameController().myGames(alice);

        assertThat(mine).extracting(GameSummary::gameId).containsExactlyInAnyOrder(asWhite.toString(), asBlack.toString());
        assertThat(mine).extracting(GameSummary::opponentName).containsOnly("Computer");
        assertThat(gameController().myGames(bob)).isEmpty();
    }

    @Test
    default void gamesCanOnlyReferenceExistingAccounts() {
        long now = System.currentTimeMillis();
        String timestamp = isPostgres() ? "now()" : String.valueOf(now);
        assertThatThrownBy(() -> sql("insert into game (id, white_player, black_player, white_account_id, status, fen, moves, created_at, updated_at) "
                + "values ('fk-" + now + "', 'user:999999', 'COMPUTER', 999999, 'IN_PROGRESS', 'x', '', " + timestamp + ", " + timestamp + ")"))
                .hasRootCauseInstanceOf(SQLException.class)
                .rootCause().message().containsIgnoringCase("foreign key");
    }
}
