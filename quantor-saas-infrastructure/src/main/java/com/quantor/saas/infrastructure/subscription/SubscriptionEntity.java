// File: quantor-saas-infrastructure/src/main/java/com/quantor/saas/infrastructure/subscription/SubscriptionEntity.java
package com.quantor.saas.infrastructure.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "subscriptions",
    uniqueConstraints = @UniqueConstraint(name = "uk_subscriptions_external", columnNames = "external_subscription_id"),
    indexes = {
        @Index(name = "ix_subscriptions_user", columnList = "user_id"),
        @Index(name = "ix_subscriptions_status", columnList = "status")
    }
)
public class SubscriptionEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "plan", nullable = false, length = 30)
  private String plan;

  @Column(name = "status", nullable = false, length = 30)
  private String status;

  @Column(name = "frozen", nullable = false)
  private boolean frozen;

  @Column(name = "frozen_at")
  private Instant frozenAt;

  @Column(name = "current_period_ends_at")
  private Instant currentPeriodEndsAt;

  @Column(name = "external_subscription_id", nullable = false, length = 100)
  private String externalSubscriptionId;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SubscriptionEntity() {}

  public SubscriptionEntity(
      UUID id,
      UUID userId,
      String plan,
      String status,
      Instant currentPeriodEndsAt,
      String externalSubscriptionId,
      Instant updatedAt
  ) {
    this.id = id;
    this.userId = userId;
    this.plan = plan;
    this.status = status;
    this.frozen = false;
    this.frozenAt = null;
    this.currentPeriodEndsAt = currentPeriodEndsAt;
    this.externalSubscriptionId = externalSubscriptionId;
    this.updatedAt = updatedAt;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getPlan() { return plan; }
  public String getStatus() { return status; }
  public boolean isFrozen() { return frozen; }
  public Instant getFrozenAt() { return frozenAt; }
  public Instant getCurrentPeriodEndsAt() { return currentPeriodEndsAt; }
  public String getExternalSubscriptionId() { return externalSubscriptionId; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void setPlan(String plan) { this.plan = plan; }
  public void setStatus(String status) { this.status = status; }
  public void setCurrentPeriodEndsAt(Instant currentPeriodEndsAt) { this.currentPeriodEndsAt = currentPeriodEndsAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

  public void freeze(Instant at) {
    this.frozen = true;
    this.frozenAt = at;
  }

  public void unfreeze() {
    this.frozen = false;
    this.frozenAt = null;
  }
}
