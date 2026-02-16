package com.quantor.api.engine;

import com.quantor.application.execution.ExecutionJob;
import com.quantor.api.saas.SubscriptionAccessService;
import com.quantor.api.security.SecurityActor;
import com.quantor.saas.infrastructure.audit.AuditLogEntity;
import com.quantor.saas.infrastructure.audit.AuditLogRepository;
import com.quantor.saas.infrastructure.engine.BotCommandEntity;
import com.quantor.saas.infrastructure.engine.BotCommandRepository;
import com.quantor.saas.infrastructure.engine.BotInstanceEntity;
import com.quantor.saas.infrastructure.engine.BotInstanceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

  public EngineInstanceService(
          SubscriptionAccessService access,
          BotInstanceRepository instances,
          BotCommandRepository commands,
          AuditLogRepository audit
  ) {
    this.access = access;
    this.instances = instances;
    this.commands = commands;
    this.audit = audit;
  }

  public String statusText() {
    List<BotInstanceEntity> all = instances.findAll();
    long running = all.stream().filter(e -> STATUS_RUNNING.equals(e.getStatus())).count();
    long paused  = all.stream().filter(e -> STATUS_PAUSED.equals(e.getStatus())).count();
    long pending = all.stream().filter(e -> STATUS_PENDING.equals(e.getStatus())).count();
    long stopped = all.stream().filter(e -> STATUS_STOPPED.equals(e.getStatus())).count();
    long error   = all.stream().filter(e -> "ERROR".equals(e.getStatus())).count();
    return "bots=" + all.size()
            + " running=" + running
            + " paused=" + paused
            + " pending=" + pending
            + " stopped=" + stopped
            + " error=" + error;
  }

  /**
   * Idempotent START:
   * - if instance for same jobKey exists: reuse/update it (NO duplicate insert)
   * - if already RUNNING/PENDING/PAUSED: return ok without creating duplicate
   * - otherwise: set PENDING + enqueue START once
   * - race-safe against concurrent inserts (job_key unique)
   *
   * IMPORTANT:
   * - Plan limit MUST be checked only for NEW bots (no instance for this jobKey),
   *   otherwise second call to same jobKey would incorrectly fail with "Plan limit reached".
   */
  @Transactional
  public EngineActionResult start(String userId, StartEngineRequest req) {
    requireAuth(userId);

    ExecutionJob job = req.toJob(userId);
    long periodMs = req.periodMs() == null ? 1000L : Math.max(250L, req.periodMs());

    final String jobKey = job.key();
    final UUID uid = UUID.fromString(userId);

    // 0) Idempotency first: if instance exists -> do NOT apply plan limit
    BotInstanceEntity existing = instances.findByJobKey(jobKey).orElse(null);
    if (existing != null) {
      if (!uid.equals(existing.getUserId())) {
        throw new IllegalArgumentException("Forbidden: job does not belong to current user");
      }

      String st = safeUpper(existing.getStatus());
      if (isActiveStatus(st)) {
        return new EngineActionResult("start", jobKey, existing.getPeriodMs());
      }

      existing.setStrategyId(job.strategyId());
      existing.setSymbol(job.symbol().asBaseQuote());
      existing.setInterval(job.timeframe().name());
      existing.setLookback(job.lookback());
      existing.setPeriodMs(periodMs);
      existing.setStatus(STATUS_PENDING);
      existing.setUpdatedAt(Instant.now());

      instances.save(existing);
      enqueueCommandOnce(existing, "START");
      return new EngineActionResult("start", jobKey, periodMs);
    }

    // 1) NEW bot only -> enforce plan limits
    access.assertCanStart(userId);

    // 2) Create new instance (race-safe)
    try {
      BotInstanceEntity e = new BotInstanceEntity();
      e.setUserId(uid);
      e.setJobKey(jobKey);
      e.setStrategyId(job.strategyId());
      e.setSymbol(job.symbol().asBaseQuote());
      e.setInterval(job.timeframe().name());
      e.setLookback(job.lookback());
      e.setPeriodMs(periodMs);
      e.setStatus(STATUS_PENDING);
      e.setCreatedAt(Instant.now());
      e.setUpdatedAt(Instant.now());

      instances.save(e);
      enqueueCommandOnce(e, "START");
      return new EngineActionResult("start", jobKey, periodMs);

    } catch (DataIntegrityViolationException dup) {
      BotInstanceEntity e = instances.findByJobKey(jobKey).orElseThrow(() -> dup);

      if (!uid.equals(e.getUserId())) {
        throw new IllegalArgumentException("Forbidden: job does not belong to current user");
      }

      String st = safeUpper(e.getStatus());
      if (!isActiveStatus(st)) {
        e.setStrategyId(job.strategyId());
        e.setSymbol(job.symbol().asBaseQuote());
        e.setInterval(job.timeframe().name());
        e.setLookback(job.lookback());
        e.setPeriodMs(periodMs);
        e.setStatus(STATUS_PENDING);
        e.setUpdatedAt(Instant.now());
        instances.save(e);
        enqueueCommandOnce(e, "START");
      }

      return new EngineActionResult("start", jobKey, periodMs);
    }
  }

  // --- stop/pause/resume via jobKey only (no symbol required) ---

  @Transactional
  public EngineActionResult stopByJobKey(String userId, String jobKey) {
    requireAuth(userId);
    if (jobKey == null || jobKey.isBlank()) throw new IllegalArgumentException("Missing field: jobKey");

    BotInstanceEntity e = requireOwnedInstance(userId, jobKey);

    // Idempotent STOP: if already STOPPED -> OK (no duplicate command)
    String st = safeUpper(e.getStatus());
    if (STATUS_STOPPED.equals(st)) {
      return new EngineActionResult("stop", jobKey, null);
    }

    e.setStatus(STATUS_PENDING);
    e.setUpdatedAt(Instant.now());
    instances.save(e);

    enqueueCommandOnce(e, "STOP");
    return new EngineActionResult("stop", jobKey, null);
  }

  @Transactional
  public EngineActionResult pauseByJobKey(String userId, String jobKey) {
    requireAuth(userId);
    if (jobKey == null || jobKey.isBlank()) throw new IllegalArgumentException("Missing field: jobKey");

    BotInstanceEntity e = requireOwnedInstance(userId, jobKey);

    // Idempotent PAUSE: if already PAUSED -> OK
    String st = safeUpper(e.getStatus());
    if (STATUS_PAUSED.equals(st)) {
      return new EngineActionResult("pause", jobKey, null);
    }

    e.setStatus(STATUS_PENDING);
    e.setUpdatedAt(Instant.now());
    instances.save(e);

    enqueueCommandOnce(e, "PAUSE");
    return new EngineActionResult("pause", jobKey, null);
  }

  @Transactional
  public EngineActionResult resumeByJobKey(String userId, String jobKey) {
    requireAuth(userId);
    if (jobKey == null || jobKey.isBlank()) throw new IllegalArgumentException("Missing field: jobKey");

    BotInstanceEntity e = requireOwnedInstance(userId, jobKey);

    // Idempotent RESUME: if already RUNNING -> OK
    String st = safeUpper(e.getStatus());
    if (STATUS_RUNNING.equals(st)) {
      return new EngineActionResult("resume", jobKey, null);
    }

    e.setStatus(STATUS_PENDING);
    e.setUpdatedAt(Instant.now());
    instances.save(e);

    enqueueCommandOnce(e, "RESUME");
    return new EngineActionResult("resume", jobKey, null);
  }

  public int activeCount(String userId) {
    return (int) instances.countByUserIdAndStatusIn(
            UUID.fromString(userId),
            List.of(STATUS_RUNNING, STATUS_PAUSED)
    );
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

  private static boolean isActiveStatus(String st) {
    return STATUS_PENDING.equals(st) || STATUS_RUNNING.equals(st) || STATUS_PAUSED.equals(st);
  }

  private static String safeUpper(String s) {
    return s == null ? "" : s.trim().toUpperCase();
  }

  /**
   * Do not enqueue duplicate commands:
   * if latest same command is already in PENDING or PROCESSING -> skip.
   */
  private void enqueueCommandOnce(BotInstanceEntity inst, String command) {
    var lastPending = commands.findTopByBotInstanceIdAndStatusOrderByCreatedAtDesc(inst.getId(), "PENDING");
    if (lastPending.isPresent() && command.equalsIgnoreCase(lastPending.get().getCommand())) return;

    var lastProcessing = commands.findTopByBotInstanceIdAndStatusOrderByCreatedAtDesc(inst.getId(), "PROCESSING");
    if (lastProcessing.isPresent() && command.equalsIgnoreCase(lastProcessing.get().getCommand())) return;

    enqueueCommand(inst, command);
  }

  private void enqueueCommand(BotInstanceEntity inst, String command) {
    BotCommandEntity cmd = new BotCommandEntity();
    cmd.setBotInstanceId(inst.getId());
    cmd.setUserId(inst.getUserId());
    cmd.setCommand(command);

    String requestId = com.quantor.api.tracing.RequestContext.requestId();
    if (requestId != null) cmd.setRequestId(requestId);

    String traceparent = com.quantor.api.tracing.RequestContext.traceparent();
    if (traceparent == null || traceparent.isBlank()) {
      traceparent = com.quantor.api.tracing.TraceparentUtil.currentTraceparentOrNull();
    }
    if (traceparent != null && !traceparent.isBlank()) cmd.setTraceparent(traceparent);

    commands.save(cmd);

    var actor = SecurityActor.current();
    audit.save(new AuditLogEntity(
            UUID.randomUUID(),
            actor.actorType(),
            actor.actorId(),
            "ENGINE_" + command,
            "BOT",
            inst.getJobKey(),
            requestId,
            Instant.now()
    ));
  }
}
