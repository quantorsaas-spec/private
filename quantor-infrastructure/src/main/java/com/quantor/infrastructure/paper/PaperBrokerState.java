package com.quantor.infrastructure.paper;

import com.quantor.domain.portfolio.PortfolioPosition;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe paper broker state (multi-symbol).
 */
public class PaperBrokerState {

    private final ConcurrentHashMap<String, BigDecimal> balances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PortfolioPosition> positions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> lastPriceBySymbol = new ConcurrentHashMap<>();

    public Map<String, BigDecimal> balances() { return balances; }
    public Map<String, PortfolioPosition> positions() { return positions; }

    public void setLastPrice(String symbol, double price) {
        lastPriceBySymbol.put(symbol, price);
    }

    public Double getLastPrice(String symbol) {
        return lastPriceBySymbol.get(symbol);
    }
}
