package com.quantor.infrastructure.exchange;

import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.ExchangePort;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
import com.quantor.domain.market.Candle;

import java.util.List;
import java.util.Objects;

/**
 * Binance implementation of the unified exchange port.
 *
 * This adapter keeps all Binance-specific mapping inside infrastructure.
 */
public final class UnifiedBinanceExchangeAdapter implements ExchangePort {

    private final BinanceExchangeAdapter legacy;

    public UnifiedBinanceExchangeAdapter(BinanceExchangeAdapter legacy) {
        this.legacy = Objects.requireNonNull(legacy);
    }

    @Override
    public ExchangeId id() {
        return ExchangeId.BINANCE;
    }

    @Override
    public List<Candle> getCandles(MarketSymbol symbol, Timeframe timeframe, int limit) throws Exception {
        return legacy.getCandles(toBinanceSymbol(symbol), toBinanceInterval(timeframe), limit);
    }

    @Override
    public void marketBuy(MarketSymbol symbol, double quantity) throws Exception {
        legacy.marketBuy(toBinanceSymbol(symbol), quantity);
    }

    @Override
    public void marketSell(MarketSymbol symbol, double quantity) throws Exception {
        legacy.marketSell(toBinanceSymbol(symbol), quantity);
    }

    private static String toBinanceSymbol(MarketSymbol s) {
        // Binance expects BASEQUOTE (e.g., BTCUSDT)
        return s.base() + s.quote();
    }

    private static String toBinanceInterval(Timeframe tf) {
        return switch (tf) {
            case M1 -> "1m";
            case M3 -> "3m";
            case M5 -> "5m";
            case M15 -> "15m";
            case M30 -> "30m";
            case H1 -> "1h";
            case H4 -> "4h";
            case D1 -> "1d";
        };
    }
}
