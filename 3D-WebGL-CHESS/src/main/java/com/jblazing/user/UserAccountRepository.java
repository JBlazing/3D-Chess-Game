package com.jblazing.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByProviderAndProviderSubject(String provider, String providerSubject);

    /** Matches the unique index on lower(username). */
    @Query("select count(u) > 0 from UserAccount u where lower(u.username) = lower(:username)")
    boolean usernameTaken(@Param("username") String username);

    /** Whether another account has the username; the account's own (in any case) doesn't count. */
    @Query("select count(u) > 0 from UserAccount u where lower(u.username) = lower(:username) and u.id <> :accountId")
    boolean usernameTakenByOther(@Param("username") String username, @Param("accountId") long accountId);

    /** Locks the row (SELECT ... FOR UPDATE where supported) so concurrent rating updates can't overwrite each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findForUpdate(@Param("id") long id);
}
