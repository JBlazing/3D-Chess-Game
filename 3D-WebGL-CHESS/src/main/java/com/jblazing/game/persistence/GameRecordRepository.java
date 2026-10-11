package com.jblazing.game.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameRecordRepository extends JpaRepository<GameRecord, String> {

    /** The 50 most recently played games where the account played either side. */
    @Query("select g from GameRecord g where g.whiteAccount.id = :accountId or g.blackAccount.id = :accountId order by g.updatedAt desc limit 50")
    List<GameRecord> findRecentForAccount(@Param("accountId") long accountId);

    /** The same for a player without an account (a guest when sign-in is disabled), by player key. */
    @Query("select g from GameRecord g where g.whitePlayer = :player or g.blackPlayer = :player order by g.updatedAt desc limit 50")
    List<GameRecord> findRecentForPlayer(@Param("player") String player);

    /** Ids of the games still being played on a clock, so their flags can be watched after a restart. */
    @Query("select g.id from GameRecord g where g.status = 'IN_PROGRESS' and g.clockInitialMs is not null")
    List<String> findTimedInProgress();
}
