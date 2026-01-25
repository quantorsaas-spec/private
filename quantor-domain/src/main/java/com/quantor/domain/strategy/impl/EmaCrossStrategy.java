package com.quantor.domain.strategy.impl;



import com.quantor.domain.market.Candle;
import com.quantor.domain.order.TradeAction;
import com.quantor.domain.strategy.AutoTuner;
import com.quantor.domain.strategy.Strategy;
import java.util.ArrayList;
import java.util.List;

/**
 * EMA crossover strategy (fast / slow).
 *
 * BUY  — fast EMA crosses slow from below.
 * SELL — fast EMA crosses slow from above.
 * HOLD — otherwise.
 */
public class EmaCrossStrategy implements Strategy {

    private int fastPeriod;
    private int slowPeriod;

    public EmaCrossStrategy(int fastPeriod, int slowPeriod) {
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
    }

    // ===== Getters / setters (required by AutoTuner) =====

    public int getFastPeriod() {
        return fastPeriod;
    }

    public int getSlowPeriod() {
        return slowPeriod;
    }

    public void setFastPeriod(int fastPeriod) {
        this.fastPeriod = fastPeriod;
    }

    public void setSlowPeriod(int slowPeriod) {
        this.slowPeriod = slowPeriod;
    }

    // ====================================================

    @Override
    public TradeAction decide(List<Candle> candles) {

        if (candles == null || candles.size() < slowPeriod + 2) {
            return TradeAction.HOLD;
        }

        // Close prices
        List<Double> closes = new ArrayList<>(candles.size());
        for (Candle c : candles) {
            closes.add(c.close());
        }

        // Calculate EMA series
        double[] fastEma = calcEmaSeries(closes, fastPeriod);
        double[] slowEma = calcEmaSeries(closes, slowPeriod);

        int last = closes.size() - 1;
        int prev = closes.size() - 2;

        double fastPrev = fastEma[prev];
        double fastLast = fastEma[last];
        double slowPrev = slowEma[prev];
        double slowLast = slowEma[last];

        // 🧪 Debug output — can be removed later
        System.out.printf(
                "[EMA] fast=%d slow=%d | fastPrev=%.4f fastLast=%.4f | slowPrev=%.4f slowLast=%.4f%n",
                fastPeriod, slowPeriod,
                fastPrev, fastLast,
                slowPrev, slowLast
        );

        // BUY
        if (fastPrev <= slowPrev && fastLast > slowLast) {
            System.out.println("📈 EMA BUY");
            return TradeAction.BUY;
        }

        // SELL
        if (fastPrev >= slowPrev && fastLast < slowLast) {
            System.out.println("📉 EMA SELL");
            return TradeAction.SELL;
        }

        return TradeAction.HOLD;
    }

    // ================= EMA ======================

    /**
     * Calculates EMA for the entire price series.
     */
    private double[] calcEmaSeries(List<Double> prices, int period) {

        double[] ema = new double[prices.size()];
        if (prices.isEmpty()) return ema;

        double k = 2.0 / (period + 1.0);

        // Start with SMA
        int start = Math.min(period, prices.size());
        double sum = 0.0;

        for (int i = 0; i < start; i++) {
            sum += prices.get(i);
        }

        double prevEma = sum / start;
        ema[start - 1] = prevEma;

        // Main EMA formula
        for (int i = start; i < prices.size(); i++) {
            double price = prices.get(i);
            prevEma = price * k + prevEma * (1 - k);
            ema[i] = prevEma;
        }

        // Fill initial values
        for (int i = 0; i < start - 1; i++) {
            ema[i] = prevEma;
        }

        return ema;
    }
}