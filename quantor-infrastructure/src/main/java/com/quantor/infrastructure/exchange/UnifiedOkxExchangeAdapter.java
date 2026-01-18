package com.quantor.infrastructure.exchange;

import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.ExchangePort;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
import com.quantor.domain.market.Candle;

import java.util.List;
import java.util.Objects;

/**
 * OKX (v5) implementation of the unified exchange port.
 *
 * <p>All OKX-specific mapping stays here in infrastructure.
 */
public final class UnifiedOkxExchangeAdapter implements ExchangePort {

    private final OkxExchangeAdapter legacy;

    public UnifiedOkxExchangeAdapter(OkxExchangeAdapter legacy) {
        this.legacy = Objects.requireNonNull(legacy, "legacy");
    }

    @Override
    public ExchangeId id() {
        return ExchangeId.OKX;
    }

    @Override
    public List<Candle> getCandles(MarketSymbol symbol, Timeframe timeframe, int limit) throws Exception {
        return legacy.getCandles(toOkxInstId(symbol), toOkxBar(timeframe), limit);
    }

    @Override
    public void marketBuy(MarketSymbol symbol, double quantity) throws Exception {
        legacy.marketBuy(toOkxInstId(symbol), quantity);
    }

    @Override
    public void marketSell(MarketSymbol symbol, double quantity) throws Exception {
        legacy.marketSell(toOkxInstId(symbol), quantity);
    }

    private static String toOkxInstId(MarketSymbol s) {
        // OKX spot uses BASE-QUOTE like BTC-USDT
        return (s.base() + "-" + s.quote()).toUpperCase();
    }

    private static String toOkxBar(Timeframe tf) {
        // OKX v5 bar examples: 1m, 5m, 15m, 30m, 1H, 4H, 1D
        return switch (tf) {
            case M1 -> "1m";
            case M3 -> "3m";
            case M5 -> "5m";
            case M15 -> "15m";
            case M30 -> "30m";
            case H1 -> "1H";
            case H4 -> "4H";
            case D1 -> "1D";
        };
    }
}
