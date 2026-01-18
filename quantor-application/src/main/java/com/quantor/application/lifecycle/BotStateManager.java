package com.quantor.application.lifecycle;

public class BotStateManager {

    private volatile BotState state = BotState.STOPPED;

    public synchronized void start() { state = BotState.RUNNING; }

    public synchronized void pause() {
        if (state == BotState.RUNNING) state = BotState.PAUSED;
    }

    public synchronized void stop() { state = BotState.STOPPED; }

    public synchronized BotState getState() { return state; }
}