package com.quantor.domain.risk;

/**
 * Basic risk manager.
 * MVP: position sizing and simple SL/TP checks.
 */
public class RiskManager {

    private double positionUSDT;
    private final double feeRate;     // e.g. 0.001 = 0.1%
    private double stopLossPct;       // e.g. 0.02 = 2%
    private double takeProfitPct;     // e.g. 0.03 = 3%

    /** Default safe settings. */
    public RiskManager() {
        this(50.0, 0.001, 0.02, 0.03);
    }

    public RiskManager(double positionUSDT, double feeRate, double stopLossPct, double takeProfitPct) {
        this.positionUSDT = positionUSDT;
        this.feeRate = feeRate;
        this.stopLossPct = stopLossPct;
        this.takeProfitPct = takeProfitPct;
    }

    public double getPositionUSDT() { return positionUSDT; }
    public void setPositionUSDT(double positionUSDT) { this.positionUSDT = positionUSDT; }

    public double getStopLossPct() { return stopLossPct; }
    public void setStopLossPct(double stopLossPct) { this.stopLossPct = stopLossPct; }

    public double getTakeProfitPct() { return takeProfitPct; }
    public void setTakeProfitPct(double takeProfitPct) { this.takeProfitPct = takeProfitPct; }

    /** Fee for given notional in quote currency. */
    public double fee(double notionalQuote) {
        return notionalQuote * feeRate;
    }

    /** Stop price for a long position. */
    public double calcStopPrice(double entryPrice) {
        if (entryPrice <= 0) return 0.0;
        return entryPrice * (1.0 - stopLossPct);
    }

    /** Take profit price for a long position. */
    public double calcTakeProfitPrice(double entryPrice) {
        if (entryPrice <= 0) return 0.0;
        return entryPrice * (1.0 + takeProfitPct);
    }

    /** Returns base quantity to buy for a given price using configured positionUSDT. */
    public double positionSize(double price) {
        if (price <= 0) return 0.0;
        return positionUSDT / price;
    }

    /** SL hit check for long position. */
    public boolean hitSL(double currentPrice, double entryPrice) {
        if (entryPrice <= 0) return false;
        return currentPrice <= calcStopPrice(entryPrice);
    }

    /** TP hit check for long position. */
    public boolean hitTP(double currentPrice, double entryPrice) {
        if (entryPrice <= 0) return false;
        return currentPrice >= calcTakeProfitPrice(entryPrice);
    }

    /**
     * MVP sizing: use up to positionUSDT but not more than equity.
     * stopPrice reserved for future risk-per-trade sizing.
     */
    public double calcPositionSize(double entryPrice, double stopPrice, double equity) {
        if (entryPrice <= 0 || equity <= 0) return 0.0;
        double notional = Math.min(positionUSDT, equity);
        if (notional <= 0) return 0.0;
        return notional / entryPrice;
    }
}
