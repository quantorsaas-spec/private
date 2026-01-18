package com.quantor.application.usecase;

import com.quantor.domain.market.Candle;
import com.quantor.domain.portfolio.PortfolioSnapshot;

import java.util.List;

public record PipelineContext(
        String symbol,
        String interval,
        List<Candle> candles,
        double lastPrice,
        PortfolioSnapshot portfolio,
        TradingMode mode
) {}
