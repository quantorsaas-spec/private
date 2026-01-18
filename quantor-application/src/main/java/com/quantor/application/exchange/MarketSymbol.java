package com.quantor.application.exchange;

import java.util.Locale;
import java.util.Objects;

/**
 * Universal symbol representation: BASE/QUOTE (e.g., BTC/USDT, ETH/USDT).
 *
 * Exchange adapters must map this to native formats (BTCUSDT, BTC-USDT, BTC_USDT, ...).
 */
public final class MarketSymbol {

    private final String base;
    private final String quote;

    public MarketSymbol(String base, String quote) {
        this.base = normalizeToken(base);
        this.quote = normalizeToken(quote);
    }

    public static MarketSymbol of(String base, String quote) {
        return new MarketSymbol(base, quote);
    }

    /**
     * Parses strings like "BTC/USDT", "btc/usdt", "BTC-USDT".
     */
    public static MarketSymbol parse(String value) {
        Objects.requireNonNull(value, "value");
        String v = value.trim();
        String[] parts;
        if (v.contains("/")) parts = v.split("/");
        else if (v.contains("-")) parts = v.split("-");
        else if (v.contains("_")) parts = v.split("_");
        else throw new IllegalArgumentException("Unsupported symbol format: " + value);
        if (parts.length != 2) throw new IllegalArgumentException("Unsupported symbol format: " + value);
        return new MarketSymbol(parts[0], parts[1]);
    }

    public String base() { return base; }

    public String quote() { return quote; }

    /**
     * Returns a stable BASE/QUOTE representation suitable for keys in portfolio/journal storage.
     */
    public String asBaseQuote() {
        return toString();
    }

    @Override
    public String toString() {
        return base + "/" + quote;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MarketSymbol other)) return false;
        return base.equals(other.base) && quote.equals(other.quote);
    }

    @Override
    public int hashCode() {
        return Objects.hash(base, quote);
    }

    private static String normalizeToken(String t) {
        Objects.requireNonNull(t, "token");
        String v = t.trim();
        if (v.isEmpty()) throw new IllegalArgumentException("Empty token");
        return v.toUpperCase(Locale.ROOT);
    }
}
