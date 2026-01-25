package com.quantor.application.execution;

public interface RunHandle {
    void stop();
    void pause();
    void resume();
    boolean isRunning();

    /** Best-effort paused state (local scheduler). */
    default boolean isPaused() { return false; }
}
