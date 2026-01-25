package com.quantor.worker.metrics;

import com.quantor.saas.infrastructure.engine.BotCommandRepository;
import com.quantor.saas.infrastructure.engine.BotInstanceRepository;
import com.quantor.worker.engine.WorkerIdentity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Minimal production metrics for the worker.
 *
 * Exposes:
 * - quantor.worker.commands.pending_due (gauge)
 * - quantor.worker.commands.processing (gauge)
 * - quantor.worker.instances.leased (gauge)
 * - counters for processed/retried/failed commands
 */
@Component
public class WorkerMetrics {

  private final Counter processed;
  private final Counter retried;
  private final Counter failed;

  public WorkerMetrics(
      MeterRegistry registry,
      BotCommandRepository commands,
      BotInstanceRepository instances,
      WorkerIdentity workerIdentity
  ) {
    String workerId = workerIdentity.id();

    registry.gauge("quantor.worker.commands.pending_due", commands, BotCommandRepository::countDuePending);
    registry.gauge("quantor.worker.commands.processing", commands, BotCommandRepository::countProcessing);
    registry.gauge("quantor.worker.instances.leased", instances, r -> r.countActiveLeasesByOwner(workerId));

    this.processed = Counter.builder("quantor.worker.commands.processed")
        .description("Commands successfully processed")
        .register(registry);
    this.retried = Counter.builder("quantor.worker.commands.retried")
        .description("Commands scheduled for retry")
        .register(registry);
    this.failed = Counter.builder("quantor.worker.commands.failed")
        .description("Commands permanently failed")
        .register(registry);
  }

  public void incProcessed() {
    processed.increment();
  }

  public void incRetried() {
    retried.increment();
  }

  public void incFailed() {
    failed.increment();
  }
}
