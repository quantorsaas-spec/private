package com.quantor.domain.trade;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Minimal trade state for product-grade behavior.
 */
public class TradeContext {

    private boolean inPosition = false;
    private double entryPrice = 0.0;
    private double positionQty = 0.0;

    private long lastProcessedCandleCloseTimeMs = -1L;

    private long lastTradeTimeMs = -1L;
    private int tradesToday = 0;
    private LocalDate tradesDay = LocalDate.now(ZoneOffset.UTC);

    public boolean isInPosition() { return inPosition; }
    public void setInPosition(boolean inPosition) { this.inPosition = inPosition; }

    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }

    public double getPositionQty() { return positionQty; }
    public void setPositionQty(double positionQty) { this.positionQty = positionQty; }

    public long getLastProcessedCandleCloseTimeMs() { return lastProcessedCandleCloseTimeMs; }
    public void setLastProcessedCandleCloseTimeMs(long t) { this.lastProcessedCandleCloseTimeMs = t; }

    public long getLastTradeTimeMs() { return lastTradeTimeMs; }
    public int getTradesToday() { return tradesToday; }

    public void markTrade(long tradeTimeMs) {
        LocalDate day = Instant.ofEpochMilli(tradeTimeMs).atZone(ZoneOffset.UTC).toLocalDate();
        if (!day.equals(tradesDay)) {
            tradesDay = day;
            tradesToday = 0;
        }
        tradesToday++;
        lastTradeTimeMs = tradeTimeMs;
    }
}