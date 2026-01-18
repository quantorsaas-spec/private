package com.quantor.domain.strategy.impl;



import com.quantor.domain.market.Candle;
import com.quantor.domain.order.TradeAction;
import com.quantor.domain.strategy.Strategy;
import java.util.List;

/**
 * Test strategy for debugging:
 * 1: BUY
 * 2: HOLD
 * 3: SELL
 * 4: HOLD
 * 5: BUY
 * ...
 *
 * No market logic at all — just a signal generator
 * to test virtual trades, PnL, SL/TP, and Telegram logging.
 */
public class DebugStrategy implements Strategy {

    private int step = 0;

    @Override
    public TradeAction decide(List<Candle> candles) {
        step++;

        int mod = step % 4; // 1..4

        if (mod == 1) {
            return TradeAction.BUY;
        } else if (mod == 3) {
            return TradeAction.SELL;
        } else {
            return TradeAction.HOLD;
        }
    }
}