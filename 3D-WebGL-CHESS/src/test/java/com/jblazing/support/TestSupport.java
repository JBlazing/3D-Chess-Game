package com.jblazing.support;

import com.jblazing.controller.AuthController;
import com.jblazing.controller.GameController;
import com.jblazing.game.cache.GameStateCache;
import com.jblazing.game.service.GameService;
import com.jblazing.player.Player;
import com.jblazing.rating.RatingService;
import com.jblazing.user.UserAccount;
import com.jblazing.user.UserAccountRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * What the database test suites (JUnit test interfaces) can use. Implemented by {@link DatabaseTest}.
 */
public interface TestSupport {

    GameService games();

    GameController gameController();

    AuthController authController();

    RatingService ratings();

    GameStateCache cache();

    UserAccountRepository accounts();

    DataSource dataSource();

    /** Signs in through the real login hook, as Spring Security does once Google redirects back. */
    OidcUser signIn(String subject, String name);

    /** The player a request from this signed-in user resolves to (the real CurrentPlayerResolver). */
    Player playerFor(OidcUser user);

    /** A brand-new signed-in player, unique to the calling test, optionally with a given rating. */
    default Player newPlayer(String name) {
        return playerFor(signIn(name + "-" + UUID.randomUUID(), name));
    }

    default Player newPlayer(String name, int rating) {
        Player player = newPlayer(name);
        UserAccount account = account(player);
        account.setRating(rating);
        accounts().save(account);
        return player;
    }

    default UserAccount account(Player player) {
        return accounts().findById(player.accountId()).orElseThrow();
    }

    void sql(String statement);

    /** An SQL expression for "this many minutes ago" in an updated_at column. */
    default String minutesAgo(int minutes) {
        return "now() - interval '" + minutes + " minutes'";
    }

    /** The HTTP status a call fails with, or 0 if it succeeds. */
    default int statusOf(Runnable call) {
        try {
            call.run();
            return 0;
        } catch (ResponseStatusException e) {
            return e.getStatusCode().value();
        }
    }
}
