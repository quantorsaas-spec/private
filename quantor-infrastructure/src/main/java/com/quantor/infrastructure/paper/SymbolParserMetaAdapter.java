package com.quantor.infrastructure.paper;

import com.quantor.application.ports.ConfigPort;
import com.quantor.application.ports.SymbolMetaPort;

/**
 * Minimal symbol metadata provider based on suffix parsing (good MVP for USDT-quoted symbols).
 * For full precision, later replace with Binance exchangeInfo-based adapter.
 */
public class SymbolParserMetaAdapter implements SymbolMetaPort {

    private final String quoteAsset;

    public SymbolParserMetaAdapter(ConfigPort config) {
        this.quoteAsset = config.get("paper.quoteAsset", "USDT").trim();
    }

    @Override
    public SymbolMeta getMeta(String symbol) {
        if (symbol == null) throw new IllegalArgumentException("symbol is null");
        String s = symbol.trim().toUpperCase();
        if (!s.endsWith(quoteAsset)) {
            // fallback: assume last 4 chars are quote
            String q = s.length() >= 4 ? s.substring(s.length() - 4) : quoteAsset;
            String b = s.substring(0, Math.max(0, s.length() - q.length()));
            return new SymbolMeta(s, b, q);
        }
        String base = s.substring(0, s.length() - quoteAsset.length());
        return new SymbolMeta(s, base, quoteAsset);
    }
}
