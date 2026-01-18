package com.quantor.application.engine;

import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
import com.quantor.application.usecase.TradingPipeline;

import java.util.List;

/**
 * Paper trading engine for multi-symbol portfolio.
 * Runs pipeline tick for each symbol in a loop (caller controls scheduling).
 */
public class PaperPortfolioEngine {

    private final TradingPipeline pipeline;
    private final List<MarketSymbol> symbols;
    private final Timeframe timeframe;
    private final int lookback;

    public PaperPortfolioEngine(TradingPipeline pipeline, List<MarketSymbol> symbols, Timeframe timeframe, int lookback) {
        this.pipeline = pipeline;
        this.symbols = symbols;
        this.timeframe = timeframe;
        this.lookback = lookback;
    }

    public void runOnce() {
        for (MarketSymbol s : symbols) {
            pipeline.tick(s, timeframe, lookback);
        }
    }
}
