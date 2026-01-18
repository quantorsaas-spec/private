package com.quantor.domain.strategy.impl;


import com.quantor.domain.market.Candle;
import com.quantor.domain.order.TradeAction;
import com.quantor.domain.strategy.Strategy;
import com.quantor.domain.strategy.impl.SimpleStrategy;
import java.util.List;

    public class SimpleStrategy implements Strategy {

        private final double someBuyThreshold;
        private final double someSellThreshold;

        public SimpleStrategy(double buyThreshold, double sellThreshold) {
            this.someBuyThreshold = buyThreshold;
            this.someSellThreshold = sellThreshold;
        }

        @Override
        public TradeAction decide(List<Candle> candles) {
            double currentPrice = candles.get(candles.size() - 1).close();

            if (shouldBuy(currentPrice)) {
                return TradeAction.BUY;
            } else if (shouldSell(currentPrice)) {
                return TradeAction.SELL;
            } else {
                return TradeAction.HOLD;
            }
        }

        private boolean shouldBuy(double price) {
            return price <= someBuyThreshold;
        }

        private boolean shouldSell(double price) {
            return price >= someSellThreshold;
        }
    }