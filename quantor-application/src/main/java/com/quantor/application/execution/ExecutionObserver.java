package com.quantor.application.execution;

/**
 * Optional observer for execution telemetry (health/status).
 * Keeps UI decoupled from runner internals.
 */
public interface ExecutionObserver {
    default void onTickSuccess(ExecutionJob job) {}
    default void onTickError(ExecutionJob job, Exception error) {}
}
