package com.jblazing.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserAccountService {
    private static final Logger logger = LoggerFactory.getLogger(UserAccountService.class);

    /** 3 to 20 letters, digits or underscores. */
    public static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,20}");
    static final int MAX_NAME = 255;
    static final int MAX_PICTURE_URL = 2048;

    private final UserAccountRepository userAccountRepository;

    public UserAccountService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * Creates the account on a user's first sign-in, with the provider's name and picture; afterwards
     * refreshes the email and the last login time. Name and picture are then the player's to edit,
     * so later sign-ins leave them alone.
     */
    @Transactional
    public UserAccount recordLogin(String provider, String subject, String email, String name, String pictureUrl) {
        Instant now = Instant.now();
        UserAccount account = userAccountRepository.findByProviderAndProviderSubject(provider, subject)
                .orElseGet(() -> {
                    logger.info("Creating account for new {} user {}", provider, subject);
                    UserAccount created = new UserAccount(provider, subject, now);
                    created.setName(name);
                    created.setPictureUrl(pictureUrl);
                    return created;
                });
        account.setEmail(email);
        account.setLastLoginAt(now);
        return userAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> find(String provider, String subject) {
        return userAccountRepository.findByProviderAndProviderSubject(provider, subject);
    }

    /**
     * Whether a username could be chosen now: valid and not taken (ignoring case). With an account,
     * that account's own username counts as available, so it can change just its capitalisation.
     */
    @Transactional(readOnly = true)
    public boolean usernameAvailable(Long accountId, String username) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            return false;
        }
        return accountId == null ? !userAccountRepository.usernameTaken(username)
                : !userAccountRepository.usernameTakenByOther(username, accountId);
    }

    /**
     * Sets or changes the account's username. Games show the current username, so a change also
     * shows in past games; the old name is free for others from then on.
     *
     * @throws ResponseStatusException 400 if it isn't a valid username, 409 if another account has it
     */
    @Transactional
    public UserAccount setUsername(long accountId, String username) {
        String wanted = username == null ? "" : username.trim();
        if (!USERNAME.matcher(wanted).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usernames are 3 to 20 letters, digits or underscores");
        }
        UserAccount account = userAccountRepository.findById(accountId).orElseThrow();
        if (wanted.equals(account.getUsername())) {
            return account;
        }
        if (userAccountRepository.usernameTakenByOther(wanted, accountId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is taken");
        }
        String previous = account.getUsername();
        account.setUsername(wanted);
        try {
            // flush now, so a race with someone choosing the same name fails here, as a 409
            userAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is taken");
        }
        if (previous == null) {
            logger.info("Account {} chose the username {}", accountId, wanted);
        } else {
            logger.info("Account {} changed its username from {} to {}", accountId, previous, wanted);
        }
        return account;
    }

    /**
     * Updates the profile details the player can edit. Blank values clear them.
     *
     * @throws ResponseStatusException 400 if the name is too long or the picture isn't an http(s) URL
     */
    @Transactional
    public UserAccount updateProfile(long accountId, String name, String pictureUrl) {
        String newName = blankToNull(name);
        if (newName != null && newName.length() > MAX_NAME) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Names are at most " + MAX_NAME + " characters");
        }
        String newPicture = blankToNull(pictureUrl);
        if (newPicture != null && !isWebUrl(newPicture)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The picture must be an http or https URL");
        }
        UserAccount account = userAccountRepository.findById(accountId).orElseThrow();
        account.setName(newName);
        account.setPictureUrl(newPicture);
        return userAccountRepository.save(account);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isWebUrl(String value) {
        if (value.length() > MAX_PICTURE_URL) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
