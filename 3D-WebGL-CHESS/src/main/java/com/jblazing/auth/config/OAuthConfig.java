package com.jblazing.auth.config;

import com.jblazing.auth.AccountOidcUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
public class OAuthConfig {

    public static final String LOGIN_PAGE = "/";
    public static final String HOME_PAGE = "/index";

    /** JSON endpoints answer 401 instead of redirecting to the login page. */
    private static final RequestMatcher API = new OrRequestMatcher(
            new AntPathRequestMatcher("/game/**"),
            new AntPathRequestMatcher("/queue/**"),
            new AntPathRequestMatcher("/auth/**"));

    @Bean
    @Profile("Auth")
    public SecurityFilterChain oauthFilterChain(HttpSecurity http, AccountOidcUserService accountOidcUserService) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .requestMatchers(LOGIN_PAGE, "/static/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), API)
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint(LOGIN_PAGE), AntPathRequestMatcher.antMatcher("/**")))
                .oauth2Login(o -> o
                        .loginPage(LOGIN_PAGE)
                        .defaultSuccessUrl(HOME_PAGE, true)
                        // creates the user's account on their first sign-in
                        .userInfoEndpoint(u -> u.oidcUserService(accountOidcUserService)))
                .logout(l -> l.logoutSuccessUrl(LOGIN_PAGE))
                // sign-in always lands on HOME_PAGE, so the "page you were trying to reach" is never
                // used; saving it would create a stored session for every anonymous request
                .requestCache(c -> c.requestCache(new NullRequestCache()))
                .csrf(OAuthConfig::cookieCsrf)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }

    /** Without the Auth profile everything is open, e.g. for local development. */
    @Bean
    @Profile("!Auth")
    public SecurityFilterChain openFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(OAuthConfig::cookieCsrf)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Expose the CSRF token in a readable XSRF-TOKEN cookie so the page's fetch calls
     * can echo it back in the X-XSRF-TOKEN header.
     */
    private static void cookieCsrf(CsrfConfigurer<HttpSecurity> csrf) {
        csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler());
    }

    /** The CSRF token is loaded lazily; touch it so the cookie is written on every response. */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
