package com.quantor.infrastructure.exchange;

import com.quantor.application.ports.MarketDataPort;
import com.quantor.application.ports.OrderExecutionPort;
import com.quantor.domain.market.Candle;
import com.quantor.exchange.BybitClient;

import java.util.List;
import java.util.Objects;

/**
 * Adapter that exposes BybitClient via the existing application ports.
 */
public final class BybitExchangeAdapter implements MarketDataPort, OrderExecutionPort {

    private final BybitClient client;

    public BybitExchangeAdapter(BybitClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public List<Candle> getCandles(String symbol, String interval, int limit) throws Exception {
        return client.klinesSpot(symbol, interval, limit);
    }

    @Override
    public void marketBuy(String symbol, double quantity) throws Exception {
        client.placeSpotMarketOrder(symbol, "Buy", quantity);
    }

    @Override
    public void marketSell(String symbol, double quantity) throws Exception {
        client.placeSpotMarketOrder(symbol, "Sell", quantity);
    }
}
