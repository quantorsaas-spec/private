package com.quantor.application.execution;

import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;

/**
 * A single executable trading job (user + strategy + market).
 */
public record ExecutionJob(String userId,
                           String strategyId,
                           ExchangeId exchange,
                           ExchangeId marketDataExchange,
                           MarketSymbol symbol,
                           Timeframe timeframe,
                           int lookback) {

    public String key() {
        return userId + ":" + strategyId + ":" + exchange + ":" + marketDataExchange + ":" + symbol.asBaseQuote() + ":" + timeframe;
    }
}
