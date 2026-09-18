package ru.musicalgreetings.auth.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table (name = "sessions")
public class Session {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(
            name = "refresh_token_hash",
            nullable = false,
            columnDefinition = "bytea"
    )
    private byte[] refreshTokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Session() {
    }

    public Session(
            UUID userId,
            byte[] refreshTokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        this(null, userId, refreshTokenHash, createdAt, expiresAt);
    }

    public Session(
            UUID id,
            UUID userId,
            byte[] refreshTokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.id = id;
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash.clone();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.revokedAt = null;
    }


    public void extendUntil(Instant newExpiresAt) {
        this.expiresAt = newExpiresAt;
    }

    public void revoke(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public boolean isActiveAt(Instant time) {
        return revokedAt == null && expiresAt.isAfter(time);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public byte[] getRefreshTokenHash() {
        return refreshTokenHash.clone();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
