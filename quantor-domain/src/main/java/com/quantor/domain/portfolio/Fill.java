package com.quantor.domain.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

public class Fill {
    public enum Side { BUY, SELL }

    private final String symbol;
    private final Side side;
    private final BigDecimal qtyBase;
    private final BigDecimal price;      // quote per base
    private final BigDecimal feeQuote;
    private final Instant timestamp;

    public Fill(String symbol, Side side, BigDecimal qtyBase, BigDecimal price, BigDecimal feeQuote, Instant timestamp) {
        this.symbol = symbol;
        this.side = side;
        this.qtyBase = qtyBase;
        this.price = price;
        this.feeQuote = feeQuote;
        this.timestamp = timestamp;
    }

    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public BigDecimal getQtyBase() { return qtyBase; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getFeeQuote() { return feeQuote; }
    public Instant getTimestamp() { return timestamp; }
}
