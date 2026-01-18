package com.quantor.application.exchange;

/**
 * Helpers for parsing/formatting universal timeframes.
 */
public final class Timeframes {
    private Timeframes() {}

    public static Timeframe parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("Timeframe is null");
        String s = raw.trim().toLowerCase();
        if (s.isEmpty()) throw new IllegalArgumentException("Timeframe is empty");

        // Accept both "M1" and exchange-like strings like "1m", "5m", "1h", "4h", "1d"
        return switch (s) {
            case "m1", "1m" -> Timeframe.M1;
            case "m3", "3m" -> Timeframe.M3;
            case "m5", "5m" -> Timeframe.M5;
            case "m15", "15m" -> Timeframe.M15;
            case "m30", "30m" -> Timeframe.M30;
            case "h1", "1h" -> Timeframe.H1;
            case "h4", "4h" -> Timeframe.H4;
            case "d1", "1d" -> Timeframe.D1;
            default -> throw new IllegalArgumentException("Unsupported timeframe: " + raw);
        };
    }
}
