package com.quantor.domain.market;

/** Candle (OHLCV) with close time. */
public record Candle(
        long openTime,
        double open,
        double high,
        double low,
        double close,
        double volume,
        long closeTime
) {
    // Compatibility helpers for older code style
    public double getClose() { return close; }
    public long closeTimeMs() { return closeTime; }
}
