package com.quantor.application.service;

import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.execution.ExecutionObserver;
import com.quantor.application.execution.ExecutionRunner;
import com.quantor.application.execution.JobScheduler;
import com.quantor.application.execution.RunHandle;
import com.quantor.application.ports.NotifierPort;
import com.quantor.application.usecase.TradingPipeline;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.time.Instant;

/**
 * Starts/stops strategy sessions without UI knowing about engines.
 * This is the entry point for Telegram/CLI/Web.
 */
public class SessionService {

    private final PipelineFactory pipelineFactory;
    private final JobScheduler scheduler;
    private final NotifierPort notifier;

    private final Map<String, RunHandle> sessions = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastTick = new ConcurrentHashMap<>();
    private final Map<String, String> lastError = new ConcurrentHashMap<>();

    public SessionService(PipelineFactory pipelineFactory, JobScheduler scheduler, NotifierPort notifier) {
        this.pipelineFactory = pipelineFactory;
        this.scheduler = scheduler;
        this.notifier = notifier;
    }

    public synchronized void start(ExecutionJob job, long periodMs) {
        String key = job.key();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Session key is null/blank for job: " + job);
        }
        if (sessions.containsKey(key) && sessions.get(key).isRunning()) {
            safeNotify("⚠ Session already running: " + key);
            return;
        }
        TradingPipeline pipeline = pipelineFactory.create(job);
        ExecutionObserver observer = new ExecutionObserver() {
            @Override public void onTickSuccess(ExecutionJob j) {
                lastTick.put(key, Instant.now());
                lastError.remove(key);
            }

            @Override public void onTickError(ExecutionJob j, Exception error) {
                lastTick.put(key, Instant.now());
                lastError.put(key, error == null ? "unknown" : String.valueOf(error.getMessage()));
            }
        };

        ExecutionRunner runner = new ExecutionRunner(job, pipeline, notifier, observer);
        RunHandle handle = scheduler.scheduleAtFixedRate(key, runner, 0, Math.max(250, periodMs));
        sessions.put(key, handle);
        lastTick.putIfAbsent(key, java.time.Instant.EPOCH);
        safeNotify("▶ Session started: " + key);
    }

    public synchronized void stop(ExecutionJob job) {
        String key = job.key();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Session key is null/blank for job: " + job);
        }
        RunHandle h = sessions.remove(key);
        if (h != null) {
            h.stop();
            lastTick.remove(key);
            lastError.remove(key);
            safeNotify("⛔ Session stopped: " + key);
        } else {
            safeNotify("⚠ No session: " + key);
        }
    }

    public synchronized void pause(ExecutionJob job) {
        RunHandle h = sessions.get(job.key());
        if (h != null) { h.pause(); safeNotify("⏸ Paused: " + job.key()); }
    }

    public synchronized void resume(ExecutionJob job) {
        RunHandle h = sessions.get(job.key());
        if (h != null) { h.resume(); safeNotify("▶ Resumed: " + job.key()); }
    }

    public boolean isRunning(ExecutionJob job) {
        RunHandle h = sessions.get(job.key());
        return h != null && h.isRunning();
    }

    
    public boolean isPaused(ExecutionJob job) {
        RunHandle h = sessions.get(job.key());
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

    /** Health / telemetry snapshot for UI and support. */
    public String healthText() {
        if (sessions.isEmpty()) return "Health: OK\nNo active sessions.";

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
            if (err != null && !err.isBlank()) {
                sb.append("- ").append(key).append(" | ").append(state)
                        .append(" | lastTick=").append(ltText)
                        .append(" | lastError=").append(err)
                        .append("\n");
            } else {
                sb.append("- ").append(key).append(" | ").append(state)
                        .append(" | lastTick=").append(ltText)
                        .append("\n");
            }
        }

        return sb.toString().trim();
    }

    private void safeNotify(String msg) {
        try { notifier.send(msg); } catch (Exception ignore) {}
    }
}
