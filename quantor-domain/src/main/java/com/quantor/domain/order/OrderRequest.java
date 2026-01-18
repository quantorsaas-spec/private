package com.quantor.domain.order;


public record OrderRequest(TradeAction side, double qty, double signalPrice) { }