package com.quantor.infrastructure.exchange;

import com.quantor.application.exchange.MarketSymbol;

import java.time.Instant;
import java.util.Objects;

/**
 * A tiny, configurable execution model for PAPER trading.
 *
 * <p>It converts a requested MARKET buy/sell into a simulated fill
 * with fee + slippage applied.
 */
public final class PaperExecutionModel {

    /** Fee rate in decimals (e.g. 0.001 = 0.1%). Charged in quote currency. */
    private final double feeRate;
    /** Slippage rate in decimals (e.g. 0.0005 = 0.05%). */
    private final double slippageRate;

    public PaperExecutionModel(double feeRate, double slippageRate) {
        if (feeRate < 0) throw new IllegalArgumentException("feeRate must be >= 0");
        if (slippageRate < 0) throw new IllegalArgumentException("slippageRate must be >= 0");
        this.feeRate = feeRate;
        this.slippageRate = slippageRate;
    }

    public static PaperExecutionModel defaults() {
        // Reasonable default for crypto spot: 0.1% fee and 0.05% slippage.
        return new PaperExecutionModel(0.0010, 0.0005);
    }

    public double feeRate() { return feeRate; }

    public double slippageRate() { return slippageRate; }

    public PaperExchangeAdapter.PaperFill buyFill(MarketSymbol symbol, double qty, double lastPrice) {
        Objects.requireNonNull(symbol, "symbol");
        double execPrice = lastPrice * (1.0 + slippageRate);
        double notional = qty * execPrice;
        double fee = notional * feeRate;
        return new PaperExchangeAdapter.PaperFill(
                Instant.now(), symbol, PaperExchangeAdapter.PaperFill.Side.BUY,
                qty, execPrice, notional, fee, slippageRate);
    }

    public PaperExchangeAdapter.PaperFill sellFill(MarketSymbol symbol, double qty, double lastPrice) {
        Objects.requireNonNull(symbol, "symbol");
        double execPrice = lastPrice * (1.0 - slippageRate);
        double notional = qty * execPrice;
        double fee = notional * feeRate;
        return new PaperExchangeAdapter.PaperFill(
                Instant.now(), symbol, PaperExchangeAdapter.PaperFill.Side.SELL,
                qty, execPrice, notional, fee, slippageRate);
    }
}
