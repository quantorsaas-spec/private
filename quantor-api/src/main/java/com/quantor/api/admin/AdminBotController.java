package com.quantor.api.admin;

import com.quantor.api.tracing.RequestContext;
import com.quantor.api.tracing.TraceparentUtil;
import com.quantor.saas.infrastructure.engine.BotCommandEntity;
import com.quantor.saas.infrastructure.engine.BotCommandRepository;
import com.quantor.saas.infrastructure.engine.BotInstanceEntity;
import com.quantor.saas.infrastructure.engine.BotInstanceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/bots")
public class AdminBotController {

  private final BotInstanceRepository instances;
  private final BotCommandRepository commands;
  private final AdminAuditService audit;

  public AdminBotController(BotInstanceRepository instances, BotCommandRepository commands, AdminAuditService audit) {
    this.instances = instances;
    this.commands = commands;
    this.audit = audit;
  }

  public record BotRow(
      UUID id,
      UUID userId,
      String jobKey,
      String strategyId,
      String symbol,
      String interval,
      int lookback,
      long periodMs,
      String status,
      String leaseOwner,
      String leaseUntil,
      String updatedAt
  ) {}

  @GetMapping
  public List<BotRow> list(@RequestParam(defaultValue = "100") int limit) {
    int size = Math.max(1, Math.min(500, limit));
    var page = instances.findAll(PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
    List<BotRow> res = new ArrayList<>();
    for (BotInstanceEntity e : page.getContent()) {
      res.add(toRow(e));
    }
    return res;
  }

  @GetMapping("/{jobKey}")
  public BotRow get(@PathVariable String jobKey) {
    BotInstanceEntity e = instances.findByJobKey(jobKey)
        .orElseThrow(() -> new IllegalArgumentException("Bot not found: " + jobKey));
    return toRow(e);
  }

  /**
   * Emergency action: force-stop a bot regardless of ownership.
   * Writes STOP command to queue and clears execution lease.
   */
  @PostMapping("/{jobKey}/force-stop")
  public Map<String, Object> forceStop(@AuthenticationPrincipal Jwt jwt, @PathVariable String jobKey) {
    UUID adminId = jwt == null ? null : UUID.fromString(jwt.getSubject());

    BotInstanceEntity e = instances.findByJobKey(jobKey)
        .orElseThrow(() -> new IllegalArgumentException("Bot not found: " + jobKey));

    // mark pending and clear lease so any worker can pick up STOP quickly
    e.setStatus("PENDING");
    e.setLeaseOwner(null);
    e.setLeaseUntil(null);
    e.setUpdatedAt(Instant.now());
    instances.save(e);

    enqueueStop(e);

    if (adminId != null) audit.logAdmin(adminId, "BOT_FORCE_STOP", "BOT", e.getJobKey());
    return Map.of("status", "ok", "jobKey", e.getJobKey(), "queued", true);
  }

  private static BotRow toRow(BotInstanceEntity e) {
    return new BotRow(
        e.getId(),
        e.getUserId(),
        e.getJobKey(),
        e.getStrategyId(),
        e.getSymbol(),
        e.getInterval(),
        e.getLookback(),
        e.getPeriodMs(),
        e.getStatus(),
        e.getLeaseOwner(),
        e.getLeaseUntil() == null ? null : e.getLeaseUntil().toString(),
        e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString()
    );
  }

  private void enqueueStop(BotInstanceEntity inst) {
    BotCommandEntity cmd = new BotCommandEntity();
    cmd.setBotInstanceId(inst.getId());
    cmd.setUserId(inst.getUserId());
    cmd.setCommand("STOP");

    String requestId = RequestContext.requestId();
    if (requestId != null) cmd.setRequestId(requestId);
    String traceparent = RequestContext.traceparent();
    if (traceparent == null || traceparent.isBlank()) {
      traceparent = TraceparentUtil.currentTraceparentOrNull();
    }
    if (traceparent != null && !traceparent.isBlank()) cmd.setTraceparent(traceparent);

    commands.save(cmd);
  }
}
