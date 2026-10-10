package com.jblazing.user;

import com.jblazing.support.TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Every Google sign-in creates the user's account the first time, and refreshes it afterwards. */
interface AccountSignInTests extends TestSupport {

    @Test
    default void firstSignInCreatesTheAccount() {
        String subject = "first-" + UUID.randomUUID();
        assertThat(accounts().findByProviderAndProviderSubject("google", subject)).isEmpty();

        signIn(subject, "Test Player");

        UserAccount account = accounts().findByProviderAndProviderSubject("google", subject).orElseThrow();
        assertThat(account.getName()).isEqualTo("Test Player");
        assertThat(account.getEmail()).isEqualTo(subject + "@example.com");
        assertThat(account.getPictureUrl()).isEqualTo("https://example.com/" + subject + ".png");
        assertThat(account.getRating()).isEqualTo(1200);
        assertThat(account.getRatedGames()).isZero();
        assertThat(account.getLastLoginAt()).isEqualTo(account.getCreatedAt());
    }

    @Test
    default void laterSignInsUpdateTheSameAccount() throws InterruptedException {
        String subject = "again-" + UUID.randomUUID();
        signIn(subject, "Old Name");
        UserAccount first = accounts().findByProviderAndProviderSubject("google", subject).orElseThrow();
        Thread.sleep(20);

        signIn(subject, "New Name");

        UserAccount second = accounts().findByProviderAndProviderSubject("google", subject).orElseThrow();
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getName()).isEqualTo("New Name");
        assertThat(second.getCreatedAt()).isEqualTo(first.getCreatedAt());
        assertThat(second.getLastLoginAt()).isAfter(first.getLastLoginAt());
    }

    @Test
    default void differentGoogleUsersGetDifferentAccounts() {
        long before = accounts().count();
        signIn("one-" + UUID.randomUUID(), "One");
        signIn("two-" + UUID.randomUUID(), "Two");
        assertThat(accounts().count()).isEqualTo(before + 2);
    }

    @Test
    default void signedInUserEndpointDescribesTheAccount() {
        String subject = "me-" + UUID.randomUUID();
        OidcUser user = signIn(subject, "Me");
        long id = accounts().findByProviderAndProviderSubject("google", subject).orElseThrow().getId();

        Map<String, Object> body = authController().user(new OAuth2AuthenticationToken(user, user.getAuthorities(), "google")).getBody();

        assertThat(body).containsEntry("id", id)
                .containsEntry("name", "Me")
                .containsEntry("email", subject + "@example.com")
                .containsEntry("rating", 1200)
                .containsEntry("ratedGames", 0);
    }
}
