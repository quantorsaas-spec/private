// File: quantor-application/src/main/java/com/quantor/application/execution/ExecutionRunner.java
package com.quantor.application.execution;

import com.quantor.application.guard.TradingStoppedException;
import com.quantor.application.ports.NotifierPort;
import com.quantor.application.usecase.TradingPipeline;

/**
 * Thin runner: executes one pipeline tick for one job.
 *
 * STOP-FIX additions:
 * - Treat TradingStoppedException as a "fatal/stop" signal (not a generic error).
 * - Notify observer separately (onTickStop) if supported, otherwise fall back to onTickError.
 *
 * NOTE: We do NOT try to stop scheduling here because scheduler/session management is outside this class.
 * This runner just classifies the outcome correctly.
 */
public class ExecutionRunner implements Runnable {

    private final ExecutionJob job;
    private final TradingPipeline pipeline;
    private final NotifierPort notifier;
    private final ExecutionObserver observer;

    public ExecutionRunner(ExecutionJob job, TradingPipeline pipeline, NotifierPort notifier) {
        this(job, pipeline, notifier, null);
    }

    public ExecutionRunner(ExecutionJob job, TradingPipeline pipeline, NotifierPort notifier, ExecutionObserver observer) {
        this.job = job;
        this.pipeline = pipeline;
        this.notifier = notifier;
        this.observer = observer;
    }

    @Override
    public void run() {
        try {
            pipeline.tick(job.symbol(), job.timeframe(), job.lookback());
            if (observer != null) observer.onTickSuccess(job);

        } catch (TradingStoppedException e) {
            // STOP condition: subscription inactive / kill-switch / risk stop
            if (observer != null) {
                // If your ExecutionObserver does not have onTickStop, keep onTickError only.
                try {
                    observer.getClass().getMethod("onTickStop", ExecutionJob.class, String.class)
                            .invoke(observer, job, e.getMessage());
                } catch (Exception reflectionFallback) {
                    observer.onTickError(job, e);
                }
            }
            try { notifier.send("🛑 RUNNER STOP " + job.key() + ": " + e.getMessage()); } catch (Exception ignore) {}

        } catch (Exception e) {
            if (observer != null) observer.onTickError(job, e);
            try { notifier.send("❌ RUNNER " + job.key() + ": " + e.getMessage()); } catch (Exception ignore) {}
        }
    }
}
