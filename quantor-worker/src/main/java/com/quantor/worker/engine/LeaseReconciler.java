package com.quantor.worker.engine;

import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.service.SessionService;
import com.quantor.worker.util.JobParsing;
import com.quantor.saas.infrastructure.engine.BotInstanceEntity;
import com.quantor.saas.infrastructure.engine.BotInstanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ensures each RUNNING/PAUSED bot instance is executed by exactly one worker.
 *
 * Strategy:
 * - Claim expired/unowned leases using SELECT FOR UPDATE SKIP LOCKED (safe across many workers).
 * - Periodically renew leases for instances this worker owns.
 * - Reconcile in-process sessions with desired state stored in DB.
 */
@Component
public class LeaseReconciler {

  private final BotInstanceRepository instances;
  private final SessionService sessions;
  private final String workerId;
  private final Duration leaseTtl;
  private final int batchSize;

  public LeaseReconciler(
      BotInstanceRepository instances,
      SessionService sessions,
      WorkerIdentity workerIdentity,
      @Value("${quantor.worker.leaseTtlSeconds:30}") long leaseTtlSeconds,
      @Value("${quantor.worker.leaseBatchSize:50}") int batchSize
  ) {
    this.instances = instances;
    this.sessions = sessions;
    this.workerId = workerIdentity.id();
    this.leaseTtl = Duration.ofSeconds(leaseTtlSeconds);
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${quantor.worker.leasePollMs:1000}")
  public void tick() {
    reconcile();
  }

  @Transactional
  void reconcile() {
    // 1) Renew leases for instances already owned by this worker.
    instances.renewLeases(workerId, leaseTtl);

    // 2) Claim new leases that are due (unowned/expired) to keep desired state running.
    List<UUID> claimed = instances.claimDueLeases(workerId, leaseTtl, batchSize);

    // 3) Reconcile all active leases (already owned + newly claimed).
    List<UUID> active = instances.findActiveLeasesByOwner(workerId);
    Set<UUID> all = new HashSet<>(active);
    all.addAll(claimed);

    if (all.isEmpty()) return;

    List<BotInstanceEntity> bots = instances.findAllById(all);
    for (BotInstanceEntity inst : bots) {
      ensureDesiredState(inst);
      // keep lease fresh for long-running processes
      inst.setLeaseOwner(workerId);
      inst.setLeaseUntil(Instant.now().plus(leaseTtl));
      instances.save(inst);
    }
  }

  private void ensureDesiredState(BotInstanceEntity inst) {
    ExecutionJob job = new ExecutionJob(
        inst.getUserId().toString(),
        inst.getStrategyId(),
        ExchangeId.BINANCE,
        ExchangeId.BINANCE,
        JobParsing.symbol(inst.getSymbol()),
        JobParsing.timeframe(inst.getInterval()),
        inst.getLookback()
    );

    String status = inst.getStatus();
    if ("RUNNING".equals(status)) {
      if (!sessions.isRunning(job)) {
        sessions.start(job, inst.getPeriodMs());
      }
      // If it was paused previously, resume it.
      if (sessions.isPaused(job)) {
        sessions.resume(job);
      }
    } else if ("PAUSED".equals(status)) {
      if (!sessions.isRunning(job)) {
        sessions.start(job, inst.getPeriodMs());
      }
      if (!sessions.isPaused(job)) {
        sessions.pause(job);
      }
    }
  }
}
