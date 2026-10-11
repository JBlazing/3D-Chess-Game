package com.jblazing.user;

import com.jblazing.controller.AuthController.ProfileRequest;
import com.jblazing.controller.AuthController.UsernameRequest;
import com.jblazing.game.service.GameSummary;
import com.jblazing.game.service.LiveGame;
import com.jblazing.player.Player;
import com.jblazing.support.TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The account page's endpoints: a username, unique ignoring case, and the name and picture, all of
 * which the player can change. Other players see the username.
 */
interface AccountTests extends TestSupport {

    private static OAuth2AuthenticationToken token(OidcUser user) {
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");
    }

    /** A username nobody else has, built from a short prefix. */
    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private OAuth2AuthenticationToken newUser(String name) {
        return token(signIn(name + "-" + UUID.randomUUID(), name));
    }

    private Map<String, Object> me(OAuth2AuthenticationToken user) {
        return authController().user(user).getBody();
    }

    @Test
    default void newAccountsHaveNoUsernameYet() {
        assertThat(me(newUser("New"))).containsEntry("username", "");
    }

    @Test
    default void aUsernameCanBeChosen() {
        OAuth2AuthenticationToken user = newUser("Alice Smith");
        String username = unique("alice");

        Map<String, Object> body = authController().setUsername(user, new UsernameRequest("  " + username + " "));

        assertThat(body).containsEntry("username", username).containsEntry("name", "Alice Smith");
        assertThat(me(user)).containsEntry("username", username);
    }

    @Test
    default void usernamesMustBeThreeToTwentyLettersDigitsOrUnderscores() {
        OAuth2AuthenticationToken user = newUser("Bad");

        for (String bad : new String[]{"", "ab", "a".repeat(21), "has space", "dash-ed", "émile", "semi;colon"}) {
            assertThat(statusOf(() -> authController().setUsername(user, new UsernameRequest(bad)))).as(bad).isEqualTo(400);
        }
        assertThat(statusOf(() -> authController().setUsername(user, new UsernameRequest(null)))).isEqualTo(400);
        assertThat(me(user)).containsEntry("username", "");
    }

    @Test
    default void usernamesAreUniqueIgnoringCase() {
        String username = unique("Bob");
        authController().setUsername(newUser("Bob One"), new UsernameRequest(username));
        OAuth2AuthenticationToken second = newUser("Bob Two");

        assertThat(statusOf(() -> authController().setUsername(second, new UsernameRequest(username.toLowerCase())))).isEqualTo(409);
        assertThat(statusOf(() -> authController().setUsername(second, new UsernameRequest(username.toUpperCase())))).isEqualTo(409);
        assertThat(me(second)).containsEntry("username", "");
    }

    @Test
    default void theDatabaseKeepsUsernamesUniqueToo() {
        // even if two players got past the service's check at the same moment
        String username = unique("race");
        Player first = newPlayer("first"), second = newPlayer("second");
        sql("update user_account set username = '" + username + "' where id = " + first.accountId());

        assertThatThrownBy(() -> sql("update user_account set username = '" + username.toUpperCase() + "' where id = " + second.accountId()))
                .hasRootCauseInstanceOf(SQLException.class)
                .rootCause().message().contains("uk_user_account_username");
    }

    @Test
    default void aUsernameCanBeChanged() {
        OAuth2AuthenticationToken user = newUser("Carol");
        String first = unique("carol"), second = unique("carol");
        authController().setUsername(user, new UsernameRequest(first));

        authController().setUsername(user, new UsernameRequest(second));

        assertThat(me(user)).containsEntry("username", second);
        assertThat(authController().usernameAvailable(null, first)).as("the old name is free again").containsEntry("available", true);
        authController().setUsername(newUser("Someone"), new UsernameRequest(first));
    }

    @Test
    default void onlyTheCapitalisationOfYourOwnUsernameCanChange() {
        OAuth2AuthenticationToken user = newUser("Hal");
        String username = unique("hal");
        authController().setUsername(user, new UsernameRequest(username));

        authController().setUsername(user, new UsernameRequest(username.toUpperCase()));

        assertThat(me(user)).containsEntry("username", username.toUpperCase());
        assertThat(authController().setUsername(user, new UsernameRequest(username.toUpperCase())))
                .as("unchanged").containsEntry("username", username.toUpperCase());
    }

    @Test
    default void aUsernameCantBeChangedToSomeoneElses() {
        String taken = unique("ivy");
        authController().setUsername(newUser("Ivy"), new UsernameRequest(taken));
        OAuth2AuthenticationToken user = newUser("Jo");
        String mine = unique("jo");
        authController().setUsername(user, new UsernameRequest(mine));

        assertThat(statusOf(() -> authController().setUsername(user, new UsernameRequest(taken.toLowerCase())))).isEqualTo(409);
        assertThat(statusOf(() -> authController().setUsername(user, new UsernameRequest("x")))).isEqualTo(400);
        assertThat(me(user)).containsEntry("username", mine);
    }

    @Test
    default void opponentsSeeTheNewUsernameInPastGamesToo() {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        UUID gameId = games().createNewGame(alice, bob);
        String renamed = unique("bobby");

        UserAccount account = account(bob);
        account.setUsername(renamed);
        accounts().save(account);

        assertThat(games().liveGame(gameId, alice).black().name()).isEqualTo(renamed);
    }

    @Test
    default void availabilityCanBeCheckedWhileTyping() {
        String username = unique("dave");
        assertThat(authController().usernameAvailable(null, username)).containsEntry("available", true);

        OAuth2AuthenticationToken dave = newUser("Dave");
        authController().setUsername(dave, new UsernameRequest(username));

        assertThat(authController().usernameAvailable(null, username)).containsEntry("available", false);
        assertThat(authController().usernameAvailable(newUser("Other"), username.toUpperCase())).containsEntry("available", false);
        assertThat(authController().usernameAvailable(dave, username.toUpperCase()))
                .as("your own, in another case").containsEntry("available", true);
        assertThat(authController().usernameAvailable(null, "no")).as("invalid").containsEntry("available", false);
    }

    @Test
    default void theNameAndPictureCanBeEdited() {
        OAuth2AuthenticationToken user = newUser("Erin");

        Map<String, Object> body = authController().updateProfile(user, new ProfileRequest(" Erin E. ", "https://example.com/me.png"));

        assertThat(body).containsEntry("name", "Erin E.").containsEntry("picture", "https://example.com/me.png");
        assertThat(me(user)).containsEntry("name", "Erin E.").containsEntry("picture", "https://example.com/me.png");
    }

    @Test
    default void blankValuesClearTheNameAndPicture() {
        OAuth2AuthenticationToken user = newUser("Frank");

        authController().updateProfile(user, new ProfileRequest(" ", ""));

        assertThat(me(user)).containsEntry("name", "").containsEntry("picture", "");
    }

    @Test
    default void thePictureMustBeAWebLink() {
        OAuth2AuthenticationToken user = newUser("Gina");

        for (String bad : new String[]{"javascript:alert(1)", "data:image/png;base64,AAAA", "not a url", "ftp://example.com/a.png",
                "https://example.com/" + "a".repeat(2048)}) {
            assertThat(statusOf(() -> authController().updateProfile(user, new ProfileRequest("Gina", bad)))).as(bad).isEqualTo(400);
        }
        assertThat(statusOf(() -> authController().updateProfile(user, new ProfileRequest("x".repeat(256), null)))).isEqualTo(400);
        assertThat(me(user)).containsEntry("name", "Gina");
    }

    @Test
    default void signingInAgainKeepsTheEditedProfile() {
        String subject = "keep-" + UUID.randomUUID();
        OAuth2AuthenticationToken user = token(signIn(subject, "Google Name"));
        authController().updateProfile(user, new ProfileRequest("My Name", "https://example.com/mine.png"));

        signIn(subject, "Google Name");

        assertThat(me(user)).containsEntry("name", "My Name").containsEntry("picture", "https://example.com/mine.png");
    }

    @Test
    default void opponentsSeeTheUsernameInGamesAndHistory() {
        Player alice = newPlayer("alice"), bob = newPlayer("bob");
        UUID gameId = games().createNewGame(alice, bob);

        LiveGame game = games().liveGame(gameId, alice);
        assertThat(game.white().name()).isEqualTo(usernameOf(alice));
        assertThat(game.black().name()).isEqualTo(usernameOf(bob));
        assertThat(gameController().myGames(alice)).filteredOn(g -> g.gameId().equals(gameId.toString()))
                .extracting(GameSummary::opponentName).containsExactly(usernameOf(bob));
    }

    @Test
    default void anOpponentWithoutAUsernameIsJustPlayer() {
        Player alice = newPlayer("alice"), legacy = newPlayer("legacy");
        sql("update user_account set username = null where id = " + legacy.accountId());

        UUID gameId = games().createNewGame(alice, legacy);

        assertThat(games().liveGame(gameId, alice).black().name()).isEqualTo("Player");
    }
}
