package com.quantor.domain.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public class PortfolioSnapshot {
    private final Map<String, AssetBalance> balances;
    private final Map<String, PortfolioPosition> positions;
    private final BigDecimal equityQuote;
    private final String quoteAsset;
    private final Instant timestamp;

    public PortfolioSnapshot(Map<String, AssetBalance> balances,
                             Map<String, PortfolioPosition> positions,
                             BigDecimal equityQuote,
                             String quoteAsset,
                             Instant timestamp) {
        this.balances = balances;
        this.positions = positions;
        this.equityQuote = equityQuote;
        this.quoteAsset = quoteAsset;
        this.timestamp = timestamp;
    }

    public Map<String, AssetBalance> getBalances() { return Collections.unmodifiableMap(balances); }
    public Map<String, PortfolioPosition> getPositions() { return Collections.unmodifiableMap(positions); }
    public BigDecimal getEquityQuote() { return equityQuote; }
    public String getQuoteAsset() { return quoteAsset; }
    public Instant getTimestamp() { return timestamp; }
}
