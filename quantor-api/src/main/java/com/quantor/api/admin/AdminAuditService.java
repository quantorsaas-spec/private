package com.quantor.api.admin;

import com.quantor.api.tracing.RequestContext;
import com.quantor.saas.infrastructure.audit.AuditLogEntity;
import com.quantor.saas.infrastructure.audit.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AdminAuditService {

  private final AuditLogRepository audits;

  public AdminAuditService(AuditLogRepository audits) {
    this.audits = audits;
  }

  public void logAdmin(UUID adminId, String action, String targetType, String targetId) {
    audits.save(new AuditLogEntity(
        UUID.randomUUID(),
        "ADMIN",
        adminId,
        action,
        targetType,
        targetId,
        RequestContext.requestId(),
        Instant.now()
    ));
  }
}
