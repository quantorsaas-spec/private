package com.quantor.saas.infrastructure.token;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", indexes = {
  @Index(name = "ix_refresh_tokens_user", columnList = "user_id"),
  @Index(name = "ix_refresh_tokens_expires", columnList = "expires_at")
})
public class RefreshTokenEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash; // hex SHA-256

  @Column(name = "revoked", nullable = false)
  private boolean revoked;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected RefreshTokenEntity() {}

  public RefreshTokenEntity(UUID id, UUID userId, String tokenHash, boolean revoked, Instant createdAt, Instant expiresAt) {
    this.id = id;
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.revoked = revoked;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getTokenHash() { return tokenHash; }
  public boolean isRevoked() { return revoked; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getExpiresAt() { return expiresAt; }

  public void setRevoked(boolean revoked) { this.revoked = revoked; }
}
