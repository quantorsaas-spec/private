package com.quantor.application.ports;

/**
 * Core-level kill switch for any trading activity.
 * If disabled, pipeline must stop trading on every tick.
 */
public interface TradingControlPort {
    boolean isTradingEnabled();
    String disabledReason();
}
