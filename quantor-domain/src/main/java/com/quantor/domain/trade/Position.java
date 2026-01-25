package com.quantor.domain.trade;

public class Position {
    private boolean longOpen;
    private double entryPrice;
    private double[] entryFeatures; // features at entry time

    // ✅ NEW: store entry parameters
    private double entryQty;     // quantity bought
    private double feeOpenUSDT;  // entry fee in USDT

    public void openLong(double price, double[] features) {
        openLong(price, features, 0.0, 0.0);
    }

    // ✅ NEW overload: keeps backward compatibility
    public void openLong(double price, double[] features, double qty, double feeOpenUSDT) {
        this.longOpen = true;
        this.entryPrice = price;
        this.entryFeatures = features;
        this.entryQty = qty;
        this.feeOpenUSDT = feeOpenUSDT;
    }

    public void close() {
        this.longOpen = false;
        this.entryFeatures = null;
        this.entryQty = 0.0;
        this.feeOpenUSDT = 0.0;
    }

    public boolean isLongOpen() {
        return longOpen;
    }

    public double getEntryPrice() {
        return entryPrice;
    }

    public double[] getEntryFeatures() {
        return entryFeatures;
    }

    public double getEntryQty() {
        return entryQty;
    }

    public double getFeeOpenUSDT() {
        return feeOpenUSDT;
    }
}