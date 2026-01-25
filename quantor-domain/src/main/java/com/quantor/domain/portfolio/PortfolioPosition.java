package com.quantor.domain.portfolio;

import java.math.BigDecimal;

public class PortfolioPosition {
    private final String symbol;
    private final String baseAsset;
    private final String quoteAsset;

    private BigDecimal qtyBase = BigDecimal.ZERO;
    private BigDecimal avgEntryPrice = BigDecimal.ZERO; // quote per base
    private BigDecimal realizedPnlQuote = BigDecimal.ZERO;

    public PortfolioPosition(String symbol, String baseAsset, String quoteAsset) {
        this.symbol = symbol;
        this.baseAsset = baseAsset;
        this.quoteAsset = quoteAsset;
    }

    public String getSymbol() { return symbol; }
    public String getBaseAsset() { return baseAsset; }
    public String getQuoteAsset() { return quoteAsset; }

    public BigDecimal getQtyBase() { return qtyBase; }
    public void setQtyBase(BigDecimal qtyBase) { this.qtyBase = qtyBase; }

    public BigDecimal getAvgEntryPrice() { return avgEntryPrice; }
    public void setAvgEntryPrice(BigDecimal avgEntryPrice) { this.avgEntryPrice = avgEntryPrice; }

    public BigDecimal getRealizedPnlQuote() { return realizedPnlQuote; }
    public void addRealizedPnlQuote(BigDecimal delta) { this.realizedPnlQuote = this.realizedPnlQuote.add(delta); }
}
