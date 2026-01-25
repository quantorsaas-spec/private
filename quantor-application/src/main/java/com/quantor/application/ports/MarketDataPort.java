package com.quantor.application.ports;

import com.quantor.domain.market.Candle;
import java.util.List;

public interface MarketDataPort {
    List<Candle> getCandles(String symbol, String interval, int limit) throws Exception;
}
