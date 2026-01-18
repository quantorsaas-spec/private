package com.quantor.application.execution;

/**
 * Schedules execution jobs. Production implementations can be backed by queues,
 * but the app code should not care.
 */
public interface JobScheduler {
    RunHandle scheduleAtFixedRate(String key, Runnable task, long initialDelayMs, long periodMs);
}
