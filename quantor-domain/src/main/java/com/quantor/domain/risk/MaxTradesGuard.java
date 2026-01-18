package com.quantor.domain.risk;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Per-day trade count limiter.
 *
 * Caller should call {@link #onTradeExecuted()} AFTER a trade is actually executed.
 */
public final class MaxTradesGuard {

    private final int limitPerDay;
    private final Clock clock;

    private LocalDate day;
    private int count;

    public MaxTradesGuard(int limitPerDay) {
        this(limitPerDay, Clock.systemDefaultZone());
    }

    public MaxTradesGuard(int limitPerDay, Clock clock) {
        if (limitPerDay <= 0) throw new IllegalArgumentException("limitPerDay must be > 0");
        this.limitPerDay = limitPerDay;
        this.clock = Objects.requireNonNull(clock, "clock");
        resetForToday();
    }

    public synchronized boolean isBlocked() {
        rollDayIfNeeded();
        return count >= limitPerDay;
    }

    public synchronized void onTradeExecuted() {
        rollDayIfNeeded();
        count++;
    }

    public synchronized int getCount() {
        rollDayIfNeeded();
        return count;
    }

    public synchronized int getLimitPerDay() { return limitPerDay; }

    public synchronized LocalDate getDay() { return day; }

    public synchronized void resetForToday() {
        this.day = LocalDate.now(clock);
        this.count = 0;
    }

    private void rollDayIfNeeded() {
        LocalDate now = LocalDate.now(clock);
        if (!now.equals(day)) {
            day = now;
            count = 0;
        }
    }

    public synchronized String statusLine() {
        rollDayIfNeeded();
        String s = isBlocked() ? "BLOCKED" : "OK";
        return "MaxTradesGuard{day=" + day + ", count=" + count + ", limit=" + limitPerDay + ", status=" + s + "}";
    }
}
