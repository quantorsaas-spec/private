package com.quantor.domain.strategy.online;



import com.quantor.domain.market.Candle;
import java.util.List;

public interface OnlineLearnable {
    void onTrade(double pnl, List<Candle> history);
}