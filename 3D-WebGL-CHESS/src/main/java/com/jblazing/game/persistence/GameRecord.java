package com.jblazing.game.persistence;

import com.jblazing.user.UserAccount;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A game as stored in the database; the source of truth when it is no longer cached in memory. */
@Entity
@Table(name = "game")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameRecord {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "white_player", nullable = false, length = 64)
    private String whitePlayer;

    @Column(name = "black_player", nullable = false, length = 64)
    private String blackPlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "white_account_id")
    private UserAccount whiteAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "black_account_id")
    private UserAccount blackAccount;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 5)
    private String winner;

    @Column(nullable = false, length = 100)
    private String fen;

    @Column(nullable = false)
    private String moves;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Rated games are between two signed-in players; never against the computer. */
    @Column(nullable = false)
    private boolean rated;

    @Column(name = "white_rating")
    private Integer whiteRating;

    @Column(name = "black_rating")
    private Integer blackRating;

    /** How much each side's rating changed when the game ended; null until then. */
    @Column(name = "white_rating_change")
    private Integer whiteRatingChange;

    @Column(name = "black_rating_change")
    private Integer blackRatingChange;

    /** The blitz clock's time control; null for untimed games. */
    @Column(name = "clock_initial_ms")
    private Long clockInitialMs;

    @Column(name = "clock_increment_ms")
    private Long clockIncrementMs;

    /** Each side's time left when the side to move began its turn, which was at {@link #updatedAt}. */
    @Column(name = "white_clock_ms")
    private Long whiteClockMs;

    @Column(name = "black_clock_ms")
    private Long blackClockMs;

    public GameRecord(String id, String whitePlayer, String blackPlayer, UserAccount whiteAccount, UserAccount blackAccount,
                      String status, String fen, Instant createdAt) {
        this.id = id;
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
        this.whiteAccount = whiteAccount;
        this.blackAccount = blackAccount;
        this.status = status;
        this.fen = fen;
        this.moves = "";
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }
}
