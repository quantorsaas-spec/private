package com.quantor.worker.engine;

import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.service.SessionService;
import com.quantor.worker.util.JobParsing;
import com.quantor.saas.infrastructure.engine.BotInstanceEntity;
import com.quantor.saas.infrastructure.engine.BotInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;

/**
 * Graceful shutdown for production:
 * - stop in-process sessions for instances leased by this worker
 * - release leases so other workers can immediately take over
 */
@Component
public class GracefulShutdownHook {

  private static final Logger log = LoggerFactory.getLogger(GracefulShutdownHook.class);

  private final BotInstanceRepository instances;
  private final SessionService sessions;
  private final String workerId;

  public GracefulShutdownHook(BotInstanceRepository instances, SessionService sessions, WorkerIdentity workerIdentity) {
    this.instances = instances;
    this.sessions = sessions;
    this.workerId = workerIdentity.id();
  }

  @PreDestroy
  public void onShutdown() {
    try {
      releaseLeasesAndStopSessions();
    } catch (Exception e) {
      log.warn("Graceful shutdown hook failed", e);
    }
  }

  @Transactional
  void releaseLeasesAndStopSessions() {
    List<UUID> leased = instances.findActiveLeasesByOwner(workerId);
    if (leased.isEmpty()) {
      log.info("No active leases to release for worker {}", workerId);
      return;
    }

    List<BotInstanceEntity> bots = instances.findAllById(leased);
    for (BotInstanceEntity inst : bots) {
      ExecutionJob job = new ExecutionJob(
          inst.getUserId().toString(),
          inst.getStrategyId(),
          ExchangeId.BINANCE,
          ExchangeId.BINANCE,
          JobParsing.symbol(inst.getSymbol()),
          JobParsing.timeframe(inst.getInterval()),
          inst.getLookback()
      );

      // Stop local execution; desired state remains in DB (RUNNING/PAUSED), so another worker will reclaim and resume.
      if (sessions.isRunning(job)) {
        sessions.stop(job);
      }

      instances.releaseLease(inst.getId(), workerId);
    }

    log.info("Released {} leases and stopped local sessions for worker {}", leased.size(), workerId);
  }
}
