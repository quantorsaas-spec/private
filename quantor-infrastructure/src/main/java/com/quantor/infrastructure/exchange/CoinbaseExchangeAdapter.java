package com.quantor.infrastructure.exchange;

import com.quantor.application.ports.MarketDataPort;
import com.quantor.application.ports.OrderExecutionPort;
import com.quantor.domain.market.Candle;
import com.quantor.exchange.CoinbaseClient;

import java.util.List;
import java.util.Objects;

/**
 * Adapter that exposes CoinbaseClient via the existing application ports.
 */
public final class CoinbaseExchangeAdapter implements MarketDataPort, OrderExecutionPort {

    private final CoinbaseClient client;

    public CoinbaseExchangeAdapter(CoinbaseClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public List<Candle> getCandles(String symbol, String interval, int limit) throws Exception {
        // interval is expected to be granularity seconds as string ("60", "300", ...)
        int granularity = Integer.parseInt(interval);
        return client.candles(symbol, granularity, limit);
    }

    @Override
    public void marketBuy(String symbol, double quantity) throws Exception {
        client.placeMarketOrder(symbol, "buy", quantity);
    }

    @Override
    public void marketSell(String symbol, double quantity) throws Exception {
        client.placeMarketOrder(symbol, "sell", quantity);
    }
}
