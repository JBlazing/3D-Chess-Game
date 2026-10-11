package com.jblazing.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A player, created the first time they sign in. Identified by the OAuth2 provider plus that
 * provider's stable subject id. Email is refreshed from the provider on every login; name and
 * picture are taken from it on the first and are then the player's to edit. Other players see
 * the username, which the player chooses.
 */
@Entity
@Table(name = "user_account")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    @Column(length = 320)
    private String email;

    private String name;

    /** Unique ignoring case; null until the player has chosen one. */
    @Column(length = 20)
    private String username;

    @Column(name = "picture_url", length = 2048)
    private String pictureUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    /** Elo rating; see {@link com.jblazing.rating.RatingService}. */
    @Column(nullable = false)
    private int rating = 1200;

    @Column(name = "rated_games", nullable = false)
    private int ratedGames;

    /** What other players see: the username, or a placeholder until one is chosen. */
    public String displayName() {
        return username != null ? username : "Player";
    }

    public UserAccount(String provider, String providerSubject, Instant createdAt) {
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.createdAt = createdAt;
        this.lastLoginAt = createdAt;
    }
}
