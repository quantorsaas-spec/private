package com.quantor.application.ports;

public interface SymbolMetaPort {

    record SymbolMeta(String symbol, String baseAsset, String quoteAsset) {}

    /** Minimal metadata needed for portfolio accounting. */
    SymbolMeta getMeta(String symbol) throws Exception;
}
