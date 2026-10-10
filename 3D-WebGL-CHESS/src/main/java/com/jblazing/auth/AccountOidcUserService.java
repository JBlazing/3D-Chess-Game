package com.jblazing.auth;

import com.jblazing.user.UserAccountService;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Loads the signed-in user as Spring Security normally would, then creates or updates their
 * account so every user who logs in has one.
 */
@Component
@Profile("Auth")
public class AccountOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final UserAccountService userAccountService;

    public AccountOidcUserService(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser user = delegate.loadUser(request);
        try {
            userAccountService.recordLogin(
                    request.getClientRegistration().getRegistrationId(),
                    user.getSubject(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getPicture());
        } catch (DataAccessException e) {
            // fail the login cleanly (redirect to /?error) instead of a 500 page
            throw new OAuth2AuthenticationException(new OAuth2Error("account_unavailable"),
                    "Could not save the user account", e);
        }
        return user;
    }
}
