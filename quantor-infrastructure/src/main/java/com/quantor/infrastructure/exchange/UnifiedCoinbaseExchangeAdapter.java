package com.quantor.infrastructure.exchange;

import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.ExchangePort;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
import com.quantor.domain.market.Candle;

import java.util.List;
import java.util.Objects;

/**
 * Coinbase implementation of the unified exchange port (spot).
 *
 * <p>Uses Coinbase Exchange REST API product ids like BTC-USD.
 */
public final class UnifiedCoinbaseExchangeAdapter implements ExchangePort {

    private final CoinbaseExchangeAdapter legacy;

    public UnifiedCoinbaseExchangeAdapter(CoinbaseExchangeAdapter legacy) {
        this.legacy = Objects.requireNonNull(legacy, "legacy");
    }

    @Override
    public ExchangeId id() {
        return ExchangeId.COINBASE;
    }

    @Override
    public List<Candle> getCandles(MarketSymbol symbol, Timeframe timeframe, int limit) throws Exception {
        return legacy.getCandles(toCoinbaseProductId(symbol), toGranularitySeconds(timeframe), limit);
    }

    @Override
    public void marketBuy(MarketSymbol symbol, double quantity) throws Exception {
        legacy.marketBuy(toCoinbaseProductId(symbol), quantity);
    }

    @Override
    public void marketSell(MarketSymbol symbol, double quantity) throws Exception {
        legacy.marketSell(toCoinbaseProductId(symbol), quantity);
    }

    private static String toCoinbaseProductId(MarketSymbol s) {
        // Coinbase uses BASE-QUOTE, e.g. BTC-USD
        return (s.base() + "-" + s.quote()).toUpperCase();
    }

    private static String toGranularitySeconds(Timeframe tf) {
        // Coinbase Exchange API allowed granularities: 60, 300, 900, 3600, 21600, 86400
        // Quantor Timeframe includes M30, which Coinbase doesn't support directly.
        return switch (tf) {
            case M1 -> "60";
            case M3 -> throw new IllegalArgumentException("Coinbase does not support 3m granularity. Use 1m or 5m.");
            case M5 -> "300";
            case M15 -> "900";
            case M30 -> throw new IllegalArgumentException("Coinbase does not support 30m granularity. Use 15m or 1h.");
            case H1 -> "3600";
            case H4 -> "21600";
            case D1 -> "86400";
        };
    }
}
