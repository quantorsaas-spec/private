package com.quantor.application.usecase;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OrderCooldownGuard {

    private final Map<String, Instant> lastOrder = new ConcurrentHashMap<>();
    private final long cooldownSeconds;

    public OrderCooldownGuard(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public boolean allow(String key) {
        Instant now = Instant.now();
        Instant prev = lastOrder.get(key);
        if (prev != null && now.minusSeconds(cooldownSeconds).isBefore(prev)) {
            return false;
        }
        lastOrder.put(key, now);
        return true;
    }
}
