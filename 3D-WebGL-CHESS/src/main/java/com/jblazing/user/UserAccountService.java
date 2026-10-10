package com.jblazing.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class UserAccountService {
    private static final Logger logger = LoggerFactory.getLogger(UserAccountService.class);

    private final UserAccountRepository userAccountRepository;

    public UserAccountService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * Creates the account on a user's first sign-in; afterwards refreshes their profile
     * details from the provider and the last login time.
     */
    @Transactional
    public UserAccount recordLogin(String provider, String subject, String email, String name, String pictureUrl) {
        Instant now = Instant.now();
        UserAccount account = userAccountRepository.findByProviderAndProviderSubject(provider, subject)
                .orElseGet(() -> {
                    logger.info("Creating account for new {} user {}", provider, subject);
                    return new UserAccount(provider, subject, now);
                });
        account.setEmail(email);
        account.setName(name);
        account.setPictureUrl(pictureUrl);
        account.setLastLoginAt(now);
        return userAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> find(String provider, String subject) {
        return userAccountRepository.findByProviderAndProviderSubject(provider, subject);
    }
}
