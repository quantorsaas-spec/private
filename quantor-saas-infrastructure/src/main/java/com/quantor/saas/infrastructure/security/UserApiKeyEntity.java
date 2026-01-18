package com.quantor.saas.infrastructure.security;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_api_keys")
public class UserApiKeyEntity {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
  private UUID userId;

  @Column(nullable = false, length = 32)
  private String exchange; // e.g., BINANCE

  @Column(name = "api_key_enc", length = 4096)
  private String apiKeyEnc;

  @Column(name = "api_secret_enc", length = 4096)
  private String apiSecretEnc;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UserApiKeyEntity() {}

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }

  public String getExchange() { return exchange; }
  public void setExchange(String exchange) { this.exchange = exchange; }

  public String getApiKeyEnc() { return apiKeyEnc; }
  public void setApiKeyEnc(String apiKeyEnc) { this.apiKeyEnc = apiKeyEnc; }

  public String getApiSecretEnc() { return apiSecretEnc; }
  public void setApiSecretEnc(String apiSecretEnc) { this.apiSecretEnc = apiSecretEnc; }

  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
