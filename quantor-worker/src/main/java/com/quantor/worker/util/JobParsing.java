package com.quantor.worker.util;

import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
import com.quantor.application.exchange.Timeframes;

public final class JobParsing {

  private JobParsing() {}

  public static Timeframe timeframe(String raw) {
    return Timeframes.parse(raw);
  }

  public static MarketSymbol symbol(String raw) {
    if (raw == null) throw new IllegalArgumentException("Symbol is null");
    String v = raw.trim();
    if (v.isEmpty()) throw new IllegalArgumentException("Symbol is empty");

    // New format: BTC/USDT, BTC-USDT, BTC_USDT
    if (v.contains("/") || v.contains("-") || v.contains("_")) {
      return MarketSymbol.parse(v);
    }

    // Legacy format: BTCUSDT
    String u = v.toUpperCase();
    String[] quotes = {"USDT", "USDC", "BUSD", "USD", "BTC", "ETH"};
    for (String q : quotes) {
      if (u.endsWith(q) && u.length() > q.length()) {
        String base = u.substring(0, u.length() - q.length());
        return new MarketSymbol(base, q);
      }
    }

    throw new IllegalArgumentException("Unsupported symbol format: " + raw);
  }

  public static String toBinanceSymbol(MarketSymbol symbol) {
    return symbol.base() + symbol.quote();
  }
}
