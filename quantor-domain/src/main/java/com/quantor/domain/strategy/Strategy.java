package com.quantor.domain.strategy;



import com.quantor.domain.market.Candle;
import com.quantor.domain.order.TradeAction;
import java.util.List;

public interface Strategy {
    TradeAction decide(List<Candle> history);

    // ✅ so that @Override in OnlineStrategy does not break compilation
    default String getParamsSummary() {
        return "No params summary";
    }
}