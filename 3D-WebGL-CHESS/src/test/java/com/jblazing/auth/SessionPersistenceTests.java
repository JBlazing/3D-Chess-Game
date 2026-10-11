package com.jblazing.auth;

import com.jblazing.MyApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A sign-in is kept in the database (Spring Session JDBC), so it survives restarting the
 * application. The real app is started twice on the same database and used over real HTTP.
 */
abstract class SessionPersistenceTests {

    /** Properties pointing the app at the database (and Redis server) under test. */
    abstract Map<String, Object> database();

    private final HttpClient http = HttpClient.newHttpClient();

    private ConfigurableApplicationContext startApp() {
        return new SpringApplicationBuilder(MyApplication.class)
                .properties(database())
                .properties(Map.of("GOOGLE_CLIENT_ID", "test-client-id", "GOOGLE_CLIENT_SECRET", "test-secret",
                        "server.port", "0", "logging.level.root", "WARN"))
                .run();
    }

    private static URI url(ConfigurableApplicationContext app, String path) {
        return URI.create("http://localhost:" + ((WebServerApplicationContext) app).getWebServer().getPort() + path);
    }

    private HttpResponse<String> get(ConfigurableApplicationContext app, String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(url(app, path));
        if (cookie != null) request.header("Cookie", cookie);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Stores a signed-in session the way a completed Google sign-in does; returns its cookie. */
    private static String signedInSession(ConfigurableApplicationContext app, String subject) {
        Instant now = Instant.now();
        ClientRegistration google = ClientRegistration.withRegistrationId("google").clientId("test-client-id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth").tokenUri("https://oauth2.googleapis.com/token")
                .userNameAttributeName("sub").scope("openid").build();
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token").subject(subject).claim("name", "Remembered Player")
                .claim("email", subject + "@example.com").issuer("https://accounts.google.com").audience(List.of("test-client-id"))
                .issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        OidcUser user = app.getBean(AccountOidcUserService.class).loadUser(new OidcUserRequest(google,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(3600), Set.of("openid")), idToken));

        @SuppressWarnings("unchecked")
        SessionRepository<Session> sessions = app.getBean(SessionRepository.class);
        Session session = sessions.createSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT",
                new SecurityContextImpl(new OAuth2AuthenticationToken(user, user.getAuthorities(), "google")));
        sessions.save(session);
        return "SESSION=" + Base64.getEncoder().encodeToString(session.getId().getBytes());
    }

    private static long sessionsFor(ConfigurableApplicationContext app, String subject) throws Exception {
        try (Connection c = app.getBean(DataSource.class).getConnection();
             PreparedStatement s = c.prepareStatement("select count(*) from spring_session where principal_name = ?")) {
            s.setString(1, subject);
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static long allSessions(ConfigurableApplicationContext app) throws Exception {
        try (Connection c = app.getBean(DataSource.class).getConnection();
             ResultSet rs = c.createStatement().executeQuery("select count(*) from spring_session")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void aSignInSurvivesRestartingTheApp() throws Exception {
        String subject = "remembered-" + UUID.randomUUID();
        String cookie;
        try (ConfigurableApplicationContext app = startApp()) {
            cookie = signedInSession(app, subject);
            assertThat(get(app, "/auth/user", cookie).statusCode()).isEqualTo(200);
            assertThat(get(app, "/auth/user", null).statusCode()).as("without the cookie").isEqualTo(401);
        }

        try (ConfigurableApplicationContext app = startApp()) {
            HttpResponse<String> me = get(app, "/auth/user", cookie);
            assertThat(me.statusCode()).isEqualTo(200);
            assertThat(me.body()).contains("\"name\":\"Remembered Player\"");
            assertThat(get(app, "/index", cookie).statusCode()).as("the game page, not a redirect to sign in").isEqualTo(200);
        }
    }

    @Test
    void signingOutEndsTheSession() throws Exception {
        String subject = "leaving-" + UUID.randomUUID();
        try (ConfigurableApplicationContext app = startApp()) {
            String cookie = signedInSession(app, subject);
            assertThat(sessionsFor(app, subject)).isEqualTo(1);

            // sign out needs the CSRF cookie and the matching header
            String xsrf = get(app, "/auth/user", cookie).headers().allValues("set-cookie").stream()
                    .filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow().split(";")[0].substring("XSRF-TOKEN=".length());
            HttpResponse<String> logout = http.send(HttpRequest.newBuilder(url(app, "/logout"))
                    .header("Cookie", cookie + "; XSRF-TOKEN=" + xsrf).header("X-XSRF-TOKEN", xsrf)
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());

            assertThat(logout.statusCode()).isEqualTo(302);
            assertThat(logout.headers().allValues("set-cookie")).anySatisfy(c -> assertThat(c).startsWith("SESSION=;").contains("Max-Age=0"));
            assertThat(get(app, "/auth/user", cookie).statusCode()).isEqualTo(401);
            assertThat(sessionsFor(app, subject)).isZero();
        }
    }

    @Test
    void theSessionCookieOutlivesTheBrowserWindow() throws Exception {
        try (ConfigurableApplicationContext app = startApp()) {
            // starting a Google sign-in creates the session (it remembers the request, to check Google's reply)
            HttpResponse<String> start = get(app, "/oauth2/authorization/google", null);
            String setCookie = start.headers().allValues("set-cookie").stream().filter(c -> c.startsWith("SESSION=")).findFirst().orElseThrow();
            assertThat(setCookie).contains("Max-Age=2592000").contains("HttpOnly").contains("SameSite=Lax");
        }
    }

    @Test
    void anonymousRequestsDontCreateStoredSessions() throws Exception {
        try (ConfigurableApplicationContext app = startApp()) {
            long before = allSessions(app);
            for (String path : List.of("/auth/user", "/game/mine", "/index", "/history")) {
                get(app, path, null);
            }
            assertThat(allSessions(app)).isEqualTo(before);
        }
    }
}
