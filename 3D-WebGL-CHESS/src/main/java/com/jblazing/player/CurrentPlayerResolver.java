package com.jblazing.player;

import com.jblazing.user.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Supplies the {@link Player} making a request to controller methods that take one. The player
 * always comes from the session, never from the request, so a client can't act as someone else.
 */
@Component
public class CurrentPlayerResolver implements HandlerMethodArgumentResolver {

    private static final String GUEST_KEY = CurrentPlayerResolver.class.getName() + ".guest";

    private final UserAccountService userAccountService;

    public CurrentPlayerResolver(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(Player.class);
    }

    @Override
    public Player resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof OAuth2AuthenticationToken token) {
            // the account is created at sign-in; if it is gone, make the user sign in again
            return userAccountService.find(token.getAuthorizedClientRegistrationId(), token.getName())
                    .map(account -> Player.account(account.getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No account for this sign-in"));
        }
        // sign-in disabled (noauth profile): one guest identity per browser session
        HttpSession session = webRequest.getNativeRequest(HttpServletRequest.class).getSession();
        String key = (String) session.getAttribute(GUEST_KEY);
        if (key == null) {
            key = UUID.randomUUID().toString();
            session.setAttribute(GUEST_KEY, key);
        }
        return Player.guest(key);
    }
}
