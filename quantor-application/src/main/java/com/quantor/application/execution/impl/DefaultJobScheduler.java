package com.quantor.application.execution.impl;

import com.quantor.application.execution.JobScheduler;
import com.quantor.application.execution.RunHandle;

import java.util.Map;
import java.util.concurrent.*;

/**
 * MVP scheduler backed by ScheduledExecutorService.
 * Later replace with a distributed scheduler/queue while keeping the same interface.
 */
public class DefaultJobScheduler implements JobScheduler {

    private final ScheduledExecutorService executor;
    private final Map<String, Handle> handles = new ConcurrentHashMap<>();

    public DefaultJobScheduler(int threads) {
        this.executor = Executors.newScheduledThreadPool(Math.max(1, threads));
    }

    @Override
    public RunHandle scheduleAtFixedRate(String key, Runnable task, long initialDelayMs, long periodMs) {
        Handle h = new Handle(key, task, initialDelayMs, periodMs);
        h.start();
        handles.put(key, h);
        return h;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private final class Handle implements RunHandle {
        private final String key;
        private final Runnable task;
        private final long initialDelayMs;
        private final long periodMs;

        private volatile boolean running = false;
        private volatile boolean paused = false;
        private ScheduledFuture<?> future;

        private Handle(String key, Runnable task, long initialDelayMs, long periodMs) {
            this.key = key;
            this.task = task;
            this.initialDelayMs = initialDelayMs;
            this.periodMs = periodMs;
        }

        private synchronized void start() {
            if (future != null && !future.isCancelled()) return;
            future = executor.scheduleAtFixedRate(() -> {
                if (paused) return;
                task.run();
            }, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
            running = true;
        }

        @Override
        public synchronized void stop() {
            if (future != null) future.cancel(true);
            running = false;
        }

        @Override
        public void pause() {
            paused = true;
        }

        @Override
        public void resume() {
            paused = false;
        }

        @Override
        public boolean isRunning() {
            return running && (future != null) && !future.isCancelled();
        }

        @Override
        public boolean isPaused() {
            return paused;
        }
    }
}
