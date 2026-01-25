package com.quantor.domain.risk;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Daily loss limiter.
 *
 * Blocks trading for the rest of the day if equity drops below (startEquity * (1 - limitPct)).
 * Designed to be used by engines/workers. Persistence is the caller's responsibility.
 */
public final class DailyLossGuard {

    private final double limitPct; // 0.05 = 5%
    private final Clock clock;

    private LocalDate day;
    private Double dayStartEquity;
    private boolean blocked;

    public DailyLossGuard(double limitPct) {
        this(limitPct, Clock.systemDefaultZone());
    }

    public DailyLossGuard(double limitPct, Clock clock) {
        if (limitPct <= 0 || limitPct >= 1) throw new IllegalArgumentException("limitPct must be (0,1)");
        this.limitPct = limitPct;
        this.clock = Objects.requireNonNull(clock, "clock");
        resetForToday();
    }

    /** Call on every tick or before every trade. */
    public synchronized void onEquity(double equity) {
        rollDayIfNeeded();
        if (dayStartEquity == null) dayStartEquity = equity;
        if (blocked) return;

        double floor = dayStartEquity * (1.0 - limitPct);
        if (equity <= floor) blocked = true;
    }

    public synchronized boolean isBlocked() {
        rollDayIfNeeded();
        return blocked;
    }

    public synchronized double getLimitPct() { return limitPct; }

    public synchronized LocalDate getDay() { return day; }

    public synchronized Double getDayStartEquity() { return dayStartEquity; }

    public synchronized void resetForToday() {
        this.day = LocalDate.now(clock);
        this.dayStartEquity = null;
        this.blocked = false;
    }

    private void rollDayIfNeeded() {
        LocalDate now = LocalDate.now(clock);
        if (!now.equals(day)) {
            day = now;
            dayStartEquity = null;
            blocked = false;
        }
    }

    public synchronized String statusLine() {
        rollDayIfNeeded();
        String s = blocked ? "BLOCKED" : "OK";
        String start = (dayStartEquity == null) ? "n/a" : String.format(java.util.Locale.US, "%.2f", dayStartEquity);
        return "DailyLossGuard{day=" + day + ", startEquity=" + start + ", limit=" + (limitPct * 100) + "%, status=" + s + "}";
    }
}
