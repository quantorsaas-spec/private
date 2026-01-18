package com.quantor.infrastructure.exchange;

import com.quantor.application.ports.MarketDataPort;
import com.quantor.application.ports.OrderExecutionPort;
import com.quantor.domain.market.Candle;
import com.quantor.exchange.BinanceClient;

import java.util.List;
import java.util.Objects;

/**
 * Adapter that exposes BinanceClient via application ports.
 * BinanceClient stays in infrastructure.
 */
public class BinanceExchangeAdapter implements MarketDataPort, OrderExecutionPort {

    private final BinanceClient client;

    public BinanceExchangeAdapter(BinanceClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public List<Candle> getCandles(String symbol, String interval, int limit) throws Exception {
        return client.klines(symbol, interval, limit);
    }

    @Override
    public void marketBuy(String symbol, double quantity) throws Exception {
        client.marketBuyTestnet(symbol, quantity);
    }

    @Override
    public void marketSell(String symbol, double quantity) throws Exception {
        client.marketSellTestnet(symbol, quantity);
    }
}
