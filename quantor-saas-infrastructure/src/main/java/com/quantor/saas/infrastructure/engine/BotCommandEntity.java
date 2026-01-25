package com.quantor.saas.infrastructure.engine;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bot_commands")
public class BotCommandEntity {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "bot_instance_id", nullable = false, columnDefinition = "uuid")
  private UUID botInstanceId;

  @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
  private UUID userId;

  @Column(nullable = false, length = 16)
  private String command; // START|STOP|PAUSE|RESUME

  @Column(nullable = false, length = 16)
  private String status; // PENDING|PROCESSING|DONE|FAILED

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "next_run_at")
  private Instant nextRunAt;

  @Column(name = "worker_id", length = 64)
  private String workerId;

  /** Correlation id propagated from API request (best-effort). */
  @Column(name = "request_id", length = 64)
  private String requestId;

  /** W3C trace context header propagated from API request (best-effort). */
  @Column(name = "traceparent", length = 256)
  private String traceparent;

  @Column(name = "error_message", length = 512)
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  public BotCommandEntity() {}

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (status == null) status = "PENDING";
    // attempts defaults to 0
  }

  public UUID getId() { return id; }
  public UUID getBotInstanceId() { return botInstanceId; }
  public void setBotInstanceId(UUID botInstanceId) { this.botInstanceId = botInstanceId; }

  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }

  public String getCommand() { return command; }
  public void setCommand(String command) { this.command = command; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public String getWorkerId() { return workerId; }
  public void setWorkerId(String workerId) { this.workerId = workerId; }

  public String getRequestId() { return requestId; }
  public void setRequestId(String requestId) { this.requestId = requestId; }

  public String getTraceparent() { return traceparent; }
  public void setTraceparent(String traceparent) { this.traceparent = traceparent; }

  public Instant getLockedAt() { return lockedAt; }
  public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }

  public int getAttempts() { return attempts; }
  public void setAttempts(int attempts) { this.attempts = attempts; }

  public Instant getNextRunAt() { return nextRunAt; }
  public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }

  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

  public Instant getCreatedAt() { return createdAt; }
  public Instant getProcessedAt() { return processedAt; }
  public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
