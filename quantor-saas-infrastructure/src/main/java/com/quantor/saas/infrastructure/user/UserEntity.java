package com.quantor.saas.infrastructure.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class UserEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "email", nullable = false, length = 320)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "role", nullable = false, length = 20)
  private String role;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected UserEntity() {}

  public UserEntity(UUID id, String email, String passwordHash, String role, boolean enabled, Instant createdAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.enabled = enabled;
    this.createdAt = createdAt;
  }

  public UUID getId() { return id; }
  public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; }
  public String getRole() { return role; }
  public boolean isEnabled() { return enabled; }
  public Instant getCreatedAt() { return createdAt; }

  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public void setRole(String role) { this.role = role; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
