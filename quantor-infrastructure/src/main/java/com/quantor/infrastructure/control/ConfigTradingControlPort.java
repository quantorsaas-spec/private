package com.quantor.infrastructure.control;

import com.quantor.application.ports.TradingControlPort;

/**
 * Simple immutable TradingControl implementation.
 *
 * Source of truth is resolved OUTSIDE (e.g. from ConfigPort, env, DB).
 * This class only answers: enabled / disabled + reason.
 *
 * STOP-FIX compatible.
 */
public final class ConfigTradingControlPort implements TradingControlPort {

    private final boolean enabled;
    private final String reason;

    public ConfigTradingControlPort(boolean enabled, String reason) {
        this.enabled = enabled;
        this.reason = (reason == null || reason.isBlank())
                ? "Trading disabled"
                : reason;
    }

    @Override
    public boolean isTradingEnabled() {
        return enabled;
    }

    @Override
    public String disabledReason() {
        return reason;
    }
}
