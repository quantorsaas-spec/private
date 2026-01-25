package com.quantor.saas.infrastructure.audit;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "audit_log",
    indexes = {
        @Index(name = "ix_audit_log_created_at", columnList = "created_at"),
        @Index(name = "ix_audit_log_actor", columnList = "actor_type,actor_id"),
        @Index(name = "ix_audit_log_target", columnList = "target_type,target_id")
    }
)
public class AuditLogEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "actor_type", nullable = false, length = 32)
  private String actorType;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "action", nullable = false, length = 128)
  private String action;

  @Column(name = "target_type", nullable = false, length = 64)
  private String targetType;

  @Column(name = "target_id", nullable = false, length = 128)
  private String targetId;

  @Column(name = "request_id", length = 128)
  private String requestId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AuditLogEntity() {}

  public AuditLogEntity(UUID id, String actorType, UUID actorId, String action,
                        String targetType, String targetId, String requestId, Instant createdAt) {
    this.id = id;
    this.actorType = actorType;
    this.actorId = actorId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.requestId = requestId;
    this.createdAt = createdAt;
  }

  public UUID getId() { return id; }
  public String getActorType() { return actorType; }
  public UUID getActorId() { return actorId; }
  public String getAction() { return action; }
  public String getTargetType() { return targetType; }
  public String getTargetId() { return targetId; }
  public String getRequestId() { return requestId; }
  public Instant getCreatedAt() { return createdAt; }
}
