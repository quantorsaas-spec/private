package com.quantor.infrastructure.exchange;

import com.quantor.application.ports.MarketDataPort;
import com.quantor.application.ports.OrderExecutionPort;
import com.quantor.domain.market.Candle;
import com.quantor.exchange.OkxClient;

import java.util.List;
import java.util.Objects;

/**
 * Adapter that exposes OkxClient via the existing application ports.
 */
public final class OkxExchangeAdapter implements MarketDataPort, OrderExecutionPort {

    private final OkxClient client;

    public OkxExchangeAdapter(OkxClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public List<Candle> getCandles(String symbol, String interval, int limit) throws Exception {
        // symbol is expected as OKX instId like BTC-USDT
        // interval is expected as OKX bar like 1m, 1H, 4H, 1D
        return client.candles(symbol, interval, limit);
    }

    @Override
    public void marketBuy(String symbol, double quantity) throws Exception {
        client.placeSpotMarketOrder(symbol, "buy", quantity);
    }

    @Override
    public void marketSell(String symbol, double quantity) throws Exception {
        client.placeSpotMarketOrder(symbol, "sell", quantity);
    }
}
