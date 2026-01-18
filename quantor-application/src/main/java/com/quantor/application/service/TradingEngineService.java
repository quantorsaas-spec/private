package com.quantor.application.service;

import com.quantor.application.ports.NotifierPort;
import com.quantor.domain.market.Candle;
import com.quantor.domain.order.TradeAction;
import com.quantor.domain.strategy.Strategy;

import java.util.List;
import java.util.Objects;

/**
 * Pure application-level orchestrator:
 * Strategy -> action decision -> notify (execution is handled elsewhere).
 */
public class TradingEngineService {

    private final NotifierPort notifier;
    private final Strategy strategy;

    public TradingEngineService(NotifierPort notifier, Strategy strategy) {
        this.notifier = Objects.requireNonNull(notifier);
        this.strategy = Objects.requireNonNull(strategy);
    }

    public TradeAction decide(List<Candle> history) {
        return strategy.decide(history);
    }

    public void notifyDecision(String symbol, TradeAction action, double price) {
        notifier.send("[" + symbol + "] action=" + action + " price=" + price);
    }
}
