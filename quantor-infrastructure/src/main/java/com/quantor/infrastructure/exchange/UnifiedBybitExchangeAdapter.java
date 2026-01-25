package com.quantor.infrastructure.exchange;

import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.ExchangePort;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
import com.quantor.domain.market.Candle;

import java.util.List;
import java.util.Objects;

/**
 * Bybit (v5) implementation of the unified exchange port.
 *
 * <p>All Bybit-specific mapping stays here in infrastructure.
 */
public final class UnifiedBybitExchangeAdapter implements ExchangePort {

    private final BybitExchangeAdapter legacy;

    public UnifiedBybitExchangeAdapter(BybitExchangeAdapter legacy) {
        this.legacy = Objects.requireNonNull(legacy, "legacy");
    }

    @Override
    public ExchangeId id() {
        return ExchangeId.BYBIT;
    }

    @Override
    public List<Candle> getCandles(MarketSymbol symbol, Timeframe timeframe, int limit) throws Exception {
        return legacy.getCandles(toBybitSymbol(symbol), toBybitInterval(timeframe), limit);
    }

    @Override
    public void marketBuy(MarketSymbol symbol, double quantity) throws Exception {
        legacy.marketBuy(toBybitSymbol(symbol), quantity);
    }

    @Override
    public void marketSell(MarketSymbol symbol, double quantity) throws Exception {
        legacy.marketSell(toBybitSymbol(symbol), quantity);
    }

    private static String toBybitSymbol(MarketSymbol s) {
        // Bybit spot uses BASEQUOTE like BTCUSDT (uppercase)
        return (s.base() + s.quote()).toUpperCase();
    }

    private static String toBybitInterval(Timeframe tf) {
        // Bybit v5 expects: 1,3,5,15,30,60,120,... or D/W/M
        return switch (tf) {
            case M1 -> "1";
            case M3 -> "3";
            case M5 -> "5";
            case M15 -> "15";
            case M30 -> "30";
            case H1 -> "60";
            case H4 -> "240";
            case D1 -> "D";
        };
    }
}
