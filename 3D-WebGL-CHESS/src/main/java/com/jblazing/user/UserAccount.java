package com.jblazing.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A player, created the first time they sign in. Identified by the OAuth2 provider plus that
 * provider's stable subject id; email and name are profile details refreshed on every login.
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

    @Column(name = "picture_url", length = 2048)
    private String pictureUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    public UserAccount(String provider, String providerSubject, Instant createdAt) {
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.createdAt = createdAt;
        this.lastLoginAt = createdAt;
    }
}
