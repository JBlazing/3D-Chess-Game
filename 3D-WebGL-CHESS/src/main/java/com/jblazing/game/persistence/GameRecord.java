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
