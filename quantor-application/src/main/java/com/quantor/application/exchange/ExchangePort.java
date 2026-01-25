package com.quantor.application.exchange;

import com.quantor.domain.market.Candle;

import java.util.List;

/**
 * Single, exchange-agnostic port for market data + basic execution.
 *
 * Keep it minimal at first. Add methods only when the core actually needs them.
 */
public interface ExchangePort {

    ExchangeId id();

    /** Fetch historical candles. */
    List<Candle> getCandles(MarketSymbol symbol, Timeframe timeframe, int limit) throws Exception;

    /** Place a MARKET buy order (long-only MVP). */
    void marketBuy(MarketSymbol symbol, double quantity) throws Exception;

    /** Place a MARKET sell order (long-only MVP). */
    void marketSell(MarketSymbol symbol, double quantity) throws Exception;
}
