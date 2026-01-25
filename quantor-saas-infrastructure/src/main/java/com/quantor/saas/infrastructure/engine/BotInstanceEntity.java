package com.quantor.saas.infrastructure.engine;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bot_instances")
public class BotInstanceEntity {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
  private UUID userId;

  @Column(name = "job_key", nullable = false, unique = true, length = 255)
  private String jobKey;

  @Column(name = "strategy_id", nullable = false, length = 64)
  private String strategyId;

  @Column(nullable = false, length = 64)
  private String symbol;

  @Column(nullable = false, length = 32)
  private String interval;

  @Column(nullable = false)
  private int lookback;

  @Column(name = "period_ms", nullable = false)
  private long periodMs;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // --- Multi-worker execution lease (production) ---
  @Column(name = "lease_owner", length = 64)
  private String leaseOwner;

  @Column(name = "lease_until")
  private Instant leaseUntil;

  public BotInstanceEntity() {}

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }

  public String getJobKey() { return jobKey; }
  public void setJobKey(String jobKey) { this.jobKey = jobKey; }

  public String getStrategyId() { return strategyId; }
  public void setStrategyId(String strategyId) { this.strategyId = strategyId; }

  public String getSymbol() { return symbol; }
  public void setSymbol(String symbol) { this.symbol = symbol; }

  public String getInterval() { return interval; }
  public void setInterval(String interval) { this.interval = interval; }

  public int getLookback() { return lookback; }
  public void setLookback(int lookback) { this.lookback = lookback; }

  public long getPeriodMs() { return periodMs; }
  public void setPeriodMs(long periodMs) { this.periodMs = periodMs; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

  public String getLeaseOwner() { return leaseOwner; }
  public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }

  public Instant getLeaseUntil() { return leaseUntil; }
  public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
    if (id == null) id = UUID.randomUUID();
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
