package com.quantor.application.usecase;

import com.quantor.application.exchange.MarketSymbol;
import com.quantor.domain.order.TradeAction;

public record PipelineResult(
        MarketSymbol symbol,
        TradeAction action,
        boolean executed,
        String message
) {}
