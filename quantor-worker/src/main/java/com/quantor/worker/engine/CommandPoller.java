package com.quantor.worker.engine;

import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.service.SessionService;
import com.quantor.saas.infrastructure.engine.BotCommandEntity;
import com.quantor.saas.infrastructure.engine.BotCommandRepository;
import com.quantor.saas.infrastructure.engine.BotInstanceEntity;
import com.quantor.saas.infrastructure.engine.BotInstanceRepository;
import com.quantor.worker.util.JobParsing;
import com.quantor.worker.metrics.WorkerMetrics;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Production-grade command processor:
 * - claims commands with SELECT FOR UPDATE SKIP LOCKED
 * - retries with exponential backoff
 * - re-queues stuck PROCESSING commands (worker crash protection)
 */
@Component
public class CommandPoller {

  private final BotCommandRepository commands;
  private final BotInstanceRepository instances;
  private final SessionService sessions;
  private final String workerId;
  private final int batchSize;
  private final int maxAttempts;
  private final Duration processingTimeout;
  private final Duration retryBase;
  private final Duration leaseTtl;
  private final WorkerMetrics metrics;

  // OpenTelemetry tracer (wired via Micrometer -> OTLP exporter)
  private final Tracer otelTracer = GlobalOpenTelemetry.getTracer("quantor-worker");
  private final TextMapPropagator otelPropagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

  private static final TextMapGetter<java.util.Map<String, String>> TRACE_GETTER = new TextMapGetter<>() {
    @Override public Iterable<String> keys(java.util.Map<String, String> carrier) {
      return carrier == null ? java.util.List.of() : carrier.keySet();
    }
    @Override public String get(java.util.Map<String, String> carrier, String key) {
      if (carrier == null || key == null) return null;
      return carrier.get(key);
    }
  };

  public CommandPoller(
      BotCommandRepository commands,
      BotInstanceRepository instances,
      SessionService sessions,
      WorkerIdentity workerIdentity,
      WorkerMetrics metrics,
      @Value("${quantor.worker.batchSize:20}") int batchSize,
      @Value("${quantor.worker.maxAttempts:10}") int maxAttempts,
      @Value("${quantor.worker.processingTimeoutSeconds:120}") long processingTimeoutSeconds,
      @Value("${quantor.worker.retryBaseSeconds:2}") long retryBaseSeconds,
      @Value("${quantor.worker.leaseTtlSeconds:30}") long leaseTtlSeconds
  ) {
    this.commands = commands;
    this.instances = instances;
    this.sessions = sessions;
    this.workerId = workerIdentity.id();
    this.batchSize = batchSize;
    this.maxAttempts = maxAttempts;
    this.processingTimeout = Duration.ofSeconds(processingTimeoutSeconds);
    this.retryBase = Duration.ofSeconds(retryBaseSeconds);
    this.leaseTtl = Duration.ofSeconds(leaseTtlSeconds);
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${quantor.worker.pollMs:1000}")
  public void poll() {
    // 1) Crash protection: if a worker dies mid-processing, commands will be re-queued.
    Instant deadline = Instant.now().minus(processingTimeout);
    commands.requeueStuck(deadline);

    // 2) Claim a batch safely across multiple workers.
    List<BotCommandEntity> batch = commands.claimBatch(workerId, batchSize);
    for (BotCommandEntity cmd : batch) {
      process(cmd);
    }
  }

  @Transactional
  void process(BotCommandEntity cmd) {
    // propagate request correlation into worker logs (API -> DB -> worker)
    String rid = cmd.getRequestId();
    if (rid != null && !rid.isBlank()) {
      MDC.put("requestId", rid);
    }

    // Build parent context from the persisted traceparent (if any)
    Context parent = Context.current();
    String traceparent = cmd.getTraceparent();
    if (traceparent != null && !traceparent.isBlank()) {
      java.util.Map<String, String> carrier = java.util.Map.of("traceparent", traceparent);
      parent = otelPropagator.extract(parent, carrier, TRACE_GETTER);
    }

    Span span = otelTracer.spanBuilder("bot.command.process")
        .setParent(parent)
        .setSpanKind(SpanKind.CONSUMER)
        .setAttribute("quantor.command", cmd.getCommand())
        .setAttribute("quantor.bot_instance_id", String.valueOf(cmd.getBotInstanceId()))
        .setAttribute("quantor.worker_id", workerId)
        .setAttribute("quantor.request_id", rid == null ? "" : rid)
        .startSpan();

    try (Scope scope = span.makeCurrent()) {
      BotInstanceEntity inst = instances.findById(cmd.getBotInstanceId())
          .orElseThrow(() -> new IllegalArgumentException("Bot instance not found: " + cmd.getBotInstanceId()));

      ExecutionJob job = new ExecutionJob(
        inst.getUserId().toString(),
        inst.getStrategyId(),
        ExchangeId.BINANCE,
        ExchangeId.BINANCE,
        JobParsing.symbol(inst.getSymbol()),
        JobParsing.timeframe(inst.getInterval()),
        inst.getLookback()
    );

      try {
        switch (cmd.getCommand()) {
          case "START" -> {
            sessions.start(job, inst.getPeriodMs());
            inst.setStatus("RUNNING");
            inst.setLeaseOwner(workerId);
            inst.setLeaseUntil(Instant.now().plus(leaseTtl));
          }
          case "STOP" -> {
            sessions.stop(job);
            inst.setStatus("STOPPED");
            inst.setLeaseOwner(null);
            inst.setLeaseUntil(null);
          }
          case "PAUSE" -> {
            sessions.pause(job);
            inst.setStatus("PAUSED");
            // keep lease; paused sessions still belong to the same executor
          }
          case "RESUME" -> {
            sessions.resume(job);
            inst.setStatus("RUNNING");
            inst.setLeaseOwner(workerId);
            inst.setLeaseUntil(Instant.now().plus(leaseTtl));
          }
          default -> throw new IllegalArgumentException("Unknown command: " + cmd.getCommand());
        }

        instances.save(inst);
        commands.markDone(cmd.getId());
        metrics.incProcessed();
      } catch (Exception e) {
        String error = safeError(e);
        span.recordException(e);
        span.setStatus(StatusCode.ERROR, error);

        // Retry policy: exponential backoff until maxAttempts.
        if (cmd.getAttempts() < (maxAttempts - 1)) {
          Instant next = Instant.now().plus(exponentialBackoff(cmd.getAttempts()));
          commands.scheduleRetry(cmd.getId(), next, error);
          metrics.incRetried();
          // keep instance status unchanged for transient errors
        } else {
          inst.setStatus("ERROR");
          instances.save(inst);
          commands.markFailed(cmd.getId(), error);
          metrics.incFailed();
        }
      }
    } finally {
      span.end();
      if (rid != null && !rid.isBlank()) {
        MDC.remove("requestId");
      }
    }
  }


  private Duration exponentialBackoff(int attemptsSoFar) {
    // base * 2^attempts, with a reasonable cap
    long seconds = retryBase.getSeconds() * (1L << Math.min(attemptsSoFar, 10));
    seconds = Math.min(seconds, 300); // cap at 5 min
    return Duration.ofSeconds(seconds);
  }

  private static String safeError(Exception e) {
    String msg = e.getMessage();
    if (msg == null || msg.isBlank()) msg = e.getClass().getSimpleName();
    if (msg.length() > 500) msg = msg.substring(0, 500);
    return msg;
  }
}
