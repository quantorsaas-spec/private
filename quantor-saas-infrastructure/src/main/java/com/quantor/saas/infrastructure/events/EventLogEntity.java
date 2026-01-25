package com.quantor.saas.infrastructure.events;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "event_log",
    uniqueConstraints = @UniqueConstraint(name = "uk_event_log_source_event", columnNames = {"source", "event_id"}),
    indexes = {
        @Index(name = "ix_event_log_user", columnList = "user_id"),
        @Index(name = "ix_event_log_created", columnList = "created_at")
    }
)
public class EventLogEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "source", nullable = false, length = 30)
  private String source;

  @Column(name = "event_id", nullable = false, length = 128)
  private String eventId;

  @Column(name = "event_type", nullable = false, length = 128)
  private String eventType;

  @Column(name = "user_id")
  private UUID userId;

  // For PostgreSQL, Hibernate may map @Lob String to OID (large object).
  // We persist JSON as TEXT (see Flyway migration) to avoid LO semantics.
  @Column(name = "payload_json", columnDefinition = "text")
  private String payloadJson;

  @Column(name = "status", nullable = false, length = 30)
  private String status;

  // Same rationale as payloadJson: keep as TEXT.
  @Column(name = "error", columnDefinition = "text")
  private String error;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  protected EventLogEntity() {}

  public EventLogEntity(UUID id, String source, String eventId, String eventType, UUID userId, String payloadJson) {
    this.id = id;
    this.source = source;
    this.eventId = eventId;
    this.eventType = eventType;
    this.userId = userId;
    this.payloadJson = payloadJson;
    this.status = "PENDING";
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public String getSource() { return source; }
  public String getEventId() { return eventId; }
  public String getEventType() { return eventType; }
  public UUID getUserId() { return userId; }
  public String getPayloadJson() { return payloadJson; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public String getError() { return error; }
  public void setError(String error) { this.error = error; }

  public Instant getCreatedAt() { return createdAt; }

  public Instant getProcessedAt() { return processedAt; }
  public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
