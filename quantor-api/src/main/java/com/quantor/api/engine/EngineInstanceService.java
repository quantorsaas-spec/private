package com.quantor.api.engine;

import com.quantor.application.execution.ExecutionJob;
import com.quantor.api.saas.SubscriptionAccessService;
import com.quantor.api.security.SecurityActor;
import com.quantor.api.tracing.RequestContext;
import com.quantor.saas.infrastructure.audit.AuditLogEntity;
import com.quantor.saas.infrastructure.audit.AuditLogRepository;
import com.quantor.saas.infrastructure.engine.BotInstanceEntity;
import com.quantor.saas.infrastructure.engine.BotInstanceRepository;
import com.quantor.saas.infrastructure.engine.BotCommandEntity;
import com.quantor.saas.infrastructure.engine.BotCommandRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class EngineInstanceService {

  public static final String STATUS_RUNNING = "RUNNING";
  public static final String STATUS_PAUSED  = "PAUSED";
  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_STOPPED = "STOPPED";

  private final SubscriptionAccessService access;
  private final BotInstanceRepository instances;
  private final BotCommandRepository commands;
  private final AuditLogRepository audit;

  public EngineInstanceService(SubscriptionAccessService access, BotInstanceRepository instances, BotCommandRepository commands, AuditLogRepository audit) {
    this.access = access;
    this.instances = instances;
    this.commands = commands;
    this.audit = audit;
  }


  public String statusText() {
    List<BotInstanceEntity> all = instances.findAll();
    long running = all.stream().filter(e -> STATUS_RUNNING.equals(e.getStatus())).count();
    long paused = all.stream().filter(e -> STATUS_PAUSED.equals(e.getStatus())).count();
    long pending = all.stream().filter(e -> STATUS_PENDING.equals(e.getStatus())).count();
    long stopped = all.stream().filter(e -> STATUS_STOPPED.equals(e.getStatus())).count();
    long error = all.stream().filter(e -> "ERROR".equals(e.getStatus())).count();
    return "bots=" + all.size() + " running=" + running + " paused=" + paused + " pending=" + pending + " stopped=" + stopped + " error=" + error;
  }

  public EngineActionResult start(String userId, StartEngineRequest req) {
    requireAuth(userId);
    // enforce plan limits using DB-backed counts
    access.assertCanStart(userId);

    ExecutionJob job = req.toJob(userId);
    long periodMs = req.periodMs() == null ? 1000L : Math.max(250L, req.periodMs());

    BotInstanceEntity e = new BotInstanceEntity();
    e.setUserId(UUID.fromString(userId));
    e.setJobKey(job.key());
    e.setStrategyId(job.strategyId());
        // Persist stable string fields in SaaS DB.
        e.setSymbol(job.symbol().asBaseQuote());
        e.setInterval(job.timeframe().name());
    e.setLookback(job.lookback());
    e.setPeriodMs(periodMs);
    e.setStatus(STATUS_PENDING);
    instances.save(e);

    enqueueCommand(e, "START");
    return new EngineActionResult("start", job.key(), periodMs);
  }

  public EngineActionResult stop(String userId, EngineJobRequest req) {
    requireAuth(userId);
    ExecutionJob job = req.toJob(userId);
    BotInstanceEntity e = requireOwnedInstance(userId, job.key());

    e.setStatus(STATUS_PENDING);
    instances.save(e);
    enqueueCommand(e, "STOP");
    return new EngineActionResult("stop", job.key(), null);
  }

  public EngineActionResult pause(String userId, EngineJobRequest req) {
    requireAuth(userId);
    ExecutionJob job = req.toJob(userId);
    BotInstanceEntity e = requireOwnedInstance(userId, job.key());

    e.setStatus(STATUS_PENDING);
    instances.save(e);
    enqueueCommand(e, "PAUSE");
    return new EngineActionResult("pause", job.key(), null);
  }

  public EngineActionResult resume(String userId, EngineJobRequest req) {
    requireAuth(userId);
    ExecutionJob job = req.toJob(userId);
    BotInstanceEntity e = requireOwnedInstance(userId, job.key());

    e.setStatus(STATUS_PENDING);
    instances.save(e);
    enqueueCommand(e, "RESUME");
    return new EngineActionResult("resume", job.key(), null);
  }

  public int activeCount(String userId) {
    return (int) instances.countByUserIdAndStatusIn(UUID.fromString(userId), List.of(STATUS_RUNNING, STATUS_PAUSED));
  }
  private static void requireAuth(String userId) {
    if (userId == null || userId.isBlank()) throw new IllegalArgumentException("Missing auth");
  }

  private BotInstanceEntity requireOwnedInstance(String userId, String jobKey) {
    UUID uid = UUID.fromString(userId);
    BotInstanceEntity e = instances.findByJobKey(jobKey)
        .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobKey));
    if (!uid.equals(e.getUserId())) {
      throw new IllegalArgumentException("Forbidden: job does not belong to current user");
    }
    return e;
  }

  public record EngineActionResult(String action, String jobKey, Long periodMs) {}

  private void enqueueCommand(BotInstanceEntity inst, String command) {
    BotCommandEntity cmd = new BotCommandEntity();
    cmd.setBotInstanceId(inst.getId());
    cmd.setUserId(inst.getUserId());
    cmd.setCommand(command);

    // Correlation/tracing context (best-effort)
    String requestId = com.quantor.api.tracing.RequestContext.requestId();
    if (requestId != null) cmd.setRequestId(requestId);
    String traceparent = com.quantor.api.tracing.RequestContext.traceparent();
    if (traceparent == null || traceparent.isBlank()) {
      traceparent = com.quantor.api.tracing.TraceparentUtil.currentTraceparentOrNull();
    }
    if (traceparent != null && !traceparent.isBlank()) cmd.setTraceparent(traceparent);

    commands.save(cmd);

    // Audit trail (actor may be ADMIN when running in support/impersonation mode)
    var actor = SecurityActor.current();
    audit.save(new AuditLogEntity(
        UUID.randomUUID(),
        actor.actorType(),
        actor.actorId(),
        "ENGINE_" + command,
        "BOT",
        inst.getJobKey(),
        requestId,
        java.time.Instant.now()
    ));
  }
}
