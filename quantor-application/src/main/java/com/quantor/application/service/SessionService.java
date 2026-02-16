// File: quantor-application/src/main/java/com/quantor/application/service/SessionService.java
package com.quantor.application.service;

import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.execution.ExecutionObserver;
import com.quantor.application.execution.ExecutionRunner;
import com.quantor.application.execution.JobScheduler;
import com.quantor.application.execution.RunHandle;
import com.quantor.application.guard.SubscriptionRequiredException;
import com.quantor.application.ports.ConfigPort;
import com.quantor.application.ports.NotifierPort;
import com.quantor.application.ports.SubscriptionPort;
import com.quantor.application.usecase.TradingPipeline;
import com.quantor.domain.trading.StopReason;
import com.quantor.domain.trading.StopReasonCode;
import com.quantor.domain.trading.UserId;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starts/stops strategy sessions without UI knowing about engines.
 * Entry point for Telegram/CLI/Web.
 *
 * STOP-TRACE (DEBUG):
 * - Optional stack trace when stop() is called to identify who triggers auto-stop.
 * - Enable via config: quantor.runtime.debugStopTrace=true
 *
 * STOP-FIX:
 * - Global kill-switch enforced in CORE (start/resume)
 * - Billing gate (optional) enforced here + also inside TradingPipeline (defense-in-depth)
 * - Health/status snapshot
 */
public class SessionService {

    private final PipelineFactory pipelineFactory;
    private final JobScheduler scheduler;
    private final NotifierPort notifier;
    private final ConfigPort config;

    // Optional billing gate (fail-closed logic is inside SubscriptionPort default implementation)
    private final SubscriptionPort subscription;

    private final Map<String, RunHandle> sessions = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastTick = new ConcurrentHashMap<>();
    private final Map<String, String> lastError = new ConcurrentHashMap<>();

    public SessionService(
            PipelineFactory pipelineFactory,
            JobScheduler scheduler,
            NotifierPort notifier,
            ConfigPort config,
            SubscriptionPort subscription
    ) {
        this.pipelineFactory = pipelineFactory;
        this.scheduler = scheduler;
        this.notifier = notifier;
        this.config = config;
        this.subscription = subscription;
    }

    /** Backward-compatible ctor (no subscription gate). */
    public SessionService(
            PipelineFactory pipelineFactory,
            JobScheduler scheduler,
            NotifierPort notifier,
            ConfigPort config
    ) {
        this(pipelineFactory, scheduler, notifier, config, null);
    }

    /** Backward-compatible ctor (no config => cannot enforce kill-switch here). */
    public SessionService(
            PipelineFactory pipelineFactory,
            JobScheduler scheduler,
            NotifierPort notifier
    ) {
        this(pipelineFactory, scheduler, notifier, null, null);
    }

    /* =========================
       Guards / flags
       ========================= */

    private void assertTradingEnabledOrThrow() {
        if (config == null) return;

        boolean enabled = Boolean.parseBoolean(
                config.get("trading.enabled", "true")
        );
        if (enabled) return;

        String reason = config.get(
                "trading.disabledReason",
                "Trading disabled"
        );
        throw new IllegalStateException("Trading disabled: " + reason);
    }

    private void assertSubscriptionAllowsTradingOrThrow(ExecutionJob job) {
    // DEV BYPASS (local only): billing.forcePaid=true => allow
    if (config != null && Boolean.parseBoolean(config.get("billing.forcePaid", "false"))) {
        return;
    }

    if (subscription == null) return; // keep old behavior if wiring not provided

    String uid = job == null ? null : job.userId();
    if (uid == null || uid.isBlank()) {
        throw subscriptionDenied("Subscription required (missing userId)");
    }

    // SubscriptionPort is expected to be fail-closed (errors => BLOCKED).
    if (!subscription.canTrade(new UserId(uid))) {
        throw subscriptionDenied("Subscription required");
    }
}


    private SubscriptionRequiredException subscriptionDenied(String msg) {
        return new SubscriptionRequiredException(
                msg,
                StopReason.of(StopReasonCode.SUBSCRIPTION_REQUIRED, "Subscription required")
        );
    }

    private boolean debugStopTraceEnabled() {
        if (config == null) return false;
        return Boolean.parseBoolean(
                config.get("quantor.runtime.debugStopTrace", "false")
        );
    }

    /* =========================
       Lifecycle
       ========================= */

    public synchronized void start(ExecutionJob job, long periodMs) {
        assertTradingEnabledOrThrow();
        assertSubscriptionAllowsTradingOrThrow(job);

        String key = job == null ? null : job.key();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Session key is null/blank for job: " + job
            );
        }

        RunHandle existing = sessions.get(key);
        if (existing != null && existing.isRunning()) {
            safeNotify("⚠ Session already running: " + key);
            return;
        }

        TradingPipeline pipeline = pipelineFactory.create(job);

        ExecutionObserver observer = new ExecutionObserver() {
            @Override
            public void onTickSuccess(ExecutionJob j) {
                lastTick.put(key, Instant.now());
                lastError.remove(key);
            }

            @Override
            public void onTickError(ExecutionJob j, Exception error) {
                lastTick.put(key, Instant.now());
                lastError.put(
                        key,
                        error == null ? "unknown" : String.valueOf(error.getMessage())
                );
            }
        };

        ExecutionRunner runner =
                new ExecutionRunner(job, pipeline, notifier, observer);

        long safePeriod = Math.max(250, periodMs);
        RunHandle handle =
                scheduler.scheduleAtFixedRate(key, runner, 0, safePeriod);

        sessions.put(key, handle);
        lastTick.putIfAbsent(key, Instant.EPOCH);

        safeNotify("▶ Session started: " + key);
    }

    public synchronized void stop(ExecutionJob job) {
        String key = job == null ? null : job.key();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Session key is null/blank for job: " + job
            );
        }

        // DEBUG: identify who triggers stop()
        if (debugStopTraceEnabled()) {
            safeNotify("🧩 stop() called for: " + key);
            new Exception("STOP_TRACE key=" + key).printStackTrace();
        }

        RunHandle h = sessions.remove(key);
        if (h != null) {
            try {
                h.stop();
            } finally {
                lastTick.remove(key);
                lastError.remove(key);
            }
            safeNotify("⛔ Session stopped: " + key);
        } else {
            safeNotify("⚠ No session: " + key);
        }
    }

    public synchronized void pause(ExecutionJob job) {
        String key = job == null ? null : job.key();
        if (key == null || key.isBlank()) return;

        RunHandle h = sessions.get(key);
        if (h != null) {
            h.pause();
            safeNotify("⏸ Paused: " + key);
        }
    }

    public synchronized void resume(ExecutionJob job) {
        assertTradingEnabledOrThrow();
        assertSubscriptionAllowsTradingOrThrow(job);

        String key = job == null ? null : job.key();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Session key is null/blank for job: " + job
            );
        }

        RunHandle h = sessions.get(key);
        if (h != null) {
            h.resume();
            safeNotify("▶ Resumed: " + key);
        } else {
            safeNotify("⚠ No session: " + key);
        }
    }

    /* =========================
       Status / health
       ========================= */

    public boolean isRunning(ExecutionJob job) {
        String key = job == null ? null : job.key();
        if (key == null || key.isBlank()) return false;
        RunHandle h = sessions.get(key);
        return h != null && h.isRunning();
    }

    public boolean isPaused(ExecutionJob job) {
        String key = job == null ? null : job.key();
        if (key == null || key.isBlank()) return false;
        RunHandle h = sessions.get(key);
        return h != null && h.isPaused();
    }

    /** Human-readable status for Telegram/CLI. */
    public String statusText() {
        if (sessions.isEmpty()) return "No active sessions.";

        StringBuilder sb = new StringBuilder();
        sb.append("Active sessions: ").append(sessions.size()).append("\n");

        for (Map.Entry<String, RunHandle> e : sessions.entrySet()) {
            String key = e.getKey();
            RunHandle h = e.getValue();

            String state;
            if (h == null) state = "UNKNOWN";
            else if (h.isRunning()) state = h.isPaused() ? "PAUSED" : "RUNNING";
            else state = "STOPPED";

            sb.append("- ").append(key).append(" : ").append(state).append("\n");
        }
        return sb.toString().trim();
    }

    /** Health snapshot (last tick/error) for Telegram/CLI. */
    public String healthText() {
        if (sessions.isEmpty()) {
            return "Health: OK\nNo active sessions.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Health: OK\n");
        sb.append("Active sessions: ").append(sessions.size()).append("\n");

        for (Map.Entry<String, RunHandle> e : sessions.entrySet()) {
            String key = e.getKey();
            RunHandle h = e.getValue();

            String state;
            if (h == null) state = "UNKNOWN";
            else if (h.isRunning()) state = h.isPaused() ? "PAUSED" : "RUNNING";
            else state = "STOPPED";

            Instant lt = lastTick.get(key);
            String ltText = (lt == null) ? "never" : lt.toString();

            String err = lastError.get(key);

            sb.append("- ").append(key)
                    .append(" | ").append(state)
                    .append(" | lastTick=").append(ltText);

            if (err != null && !err.isBlank()) {
                sb.append(" | lastError=").append(err);
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    /* =========================
       Utils
       ========================= */

    private void safeNotify(String msg) {
        try {
            notifier.send(msg);
        } catch (Exception ignore) {
        }
    }
}
