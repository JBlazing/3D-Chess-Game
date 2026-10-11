package com.jblazing.support;

import com.jblazing.auth.AccountOidcUserService;
import com.jblazing.controller.AuthController;
import com.jblazing.controller.GameController;
import com.jblazing.game.cache.GameStateCache;
import com.jblazing.game.service.GameService;
import com.jblazing.player.CurrentPlayerResolver;
import com.jblazing.player.Player;
import com.jblazing.rating.RatingService;
import com.jblazing.user.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The whole application (Google sign-in mode) on a test database; subclasses pick the database.
 * All test classes for one database share a single application context. The chess engine gets a
 * longer timeout than in production: interpreted GraalJS on a cold, busy test JVM can take more
 * than the default to make its first moves.
 */
@SpringBootTest(properties = {"GOOGLE_CLIENT_ID=test-client-id", "GOOGLE_CLIENT_SECRET=test-secret",
        "game.chess.engine.executor.timeout=PT60S"})
public abstract class DatabaseTest implements TestSupport {

    @Autowired private GameService gameService;
    @Autowired private GameController gameController;
    @Autowired private AuthController authController;
    @Autowired private RatingService ratingService;
    @Autowired private GameStateCache gameStateCache;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private DataSource dataSource;
    @Autowired private AccountOidcUserService loginHook;
    @Autowired private CurrentPlayerResolver currentPlayerResolver;

    @Override public GameService games() { return gameService; }
    @Override public GameController gameController() { return gameController; }
    @Override public AuthController authController() { return authController; }
    @Override public RatingService ratings() { return ratingService; }
    @Override public GameStateCache cache() { return gameStateCache; }
    @Override public UserAccountRepository accounts() { return userAccountRepository; }
    @Override public DataSource dataSource() { return dataSource; }

    @Override
    public OidcUser signIn(String subject, String name) {
        Instant now = Instant.now();
        // no user-info endpoint and only the openid scope: the profile comes from the ID token, no network
        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientId("test-client-id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userNameAttributeName("sub")
                .scope("openid")
                .build();
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token").subject(subject)
                .claim("email", subject + "@example.com").claim("name", name)
                .claim("picture", "https://example.com/" + subject + ".png")
                .issuer("https://accounts.google.com").audience(List.of("test-client-id"))
                .issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token",
                now, now.plusSeconds(3600), Set.of("openid"));
        return loginHook.loadUser(new OidcUserRequest(google, accessToken, idToken));
    }

    @Override
    public Player playerFor(OidcUser user) {
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));
        try {
            // the request is only needed for guests; a signed-in player comes from the security context
            return currentPlayerResolver.resolveArgument(null, null, null, null);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Override
    public void sql(String statement) {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(statement);
        } catch (SQLException e) {
            throw new IllegalStateException(statement, e);
        }
    }
}
