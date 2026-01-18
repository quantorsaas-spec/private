package com.quantor.application.execution;

import com.quantor.application.ports.NotifierPort;
import com.quantor.application.usecase.TradingPipeline;

/**
 * Thin runner: executes one pipeline tick for one job.
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
        } catch (Exception e) {
            if (observer != null) observer.onTickError(job, e);
            try { notifier.send("❌ RUNNER " + job.key() + ": " + e.getMessage()); } catch (Exception ignore) {}
        }
    }
}
