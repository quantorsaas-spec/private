package com.quantor.api.engine;

import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
import com.quantor.application.execution.ExecutionJob;

import java.util.Locale;

/**
 * Minimal job identity request.
 */
public record EngineJobRequest(
        String userId,
        String strategyId,
        String symbol,
        String interval,
        Integer lookback
) {

    public ExecutionJob toJob(String userIdFromJwt) {
        String uid = (userIdFromJwt != null && !userIdFromJwt.isBlank())
                ? userIdFromJwt.trim()
                : required(userId, "userId");

        ExchangeId ex = ExchangeId.BINANCE;
        ExchangeId md = ExchangeId.BINANCE;

        MarketSymbol ms = MarketSymbol.parse(required(symbol, "symbol"));
        Timeframe tf = parseTimeframe(required(interval, "interval"));

        return new ExecutionJob(
                uid,
                required(strategyId, "strategyId"),
                ex,
                md,
                ms,
                tf,
                lookback == null ? 200 : Math.max(1, lookback)
        );
    }

    private static Timeframe parseTimeframe(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "1m", "m1" -> Timeframe.M1;
            case "5m", "m5" -> Timeframe.M5;
            case "15m", "m15" -> Timeframe.M15;
            case "30m", "m30" -> Timeframe.M30;
            case "1h", "h1" -> Timeframe.H1;
            case "4h", "h4" -> Timeframe.H4;
            case "1d", "d1" -> Timeframe.D1;
            default -> throw new IllegalArgumentException("Unsupported timeframe: " + raw);
        };
    }

    private static String required(String v, String name) {
        if (v == null || v.isBlank())
            throw new IllegalArgumentException("Missing field: " + name);
        return v.trim();
    }
}
