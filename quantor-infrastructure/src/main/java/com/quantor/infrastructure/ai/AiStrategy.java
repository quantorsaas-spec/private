package com.quantor.infrastructure.ai;

import com.quantor.domain.market.Candle;
import com.quantor.domain.order.TradeAction;
import com.quantor.domain.strategy.Strategy;
import com.quantor.exchange.ChatGptClient;

import java.util.List;

/**
 * AI strategy:
 *  - sends a request to ChatGPT
 *  - if an error or unclear response occurs, falls back to another strategy (e.g. EMA)
 */
public class AiStrategy implements Strategy {

    private final ChatGptClient ai;
    private final Strategy fallback;
    private final String symbol;
    private final String interval;
    private final int lookback;

    public AiStrategy(ChatGptClient ai,
                      Strategy fallback,
                      String symbol,
                      String interval,
                      int lookback) {
        this.ai = ai;
        this.fallback = fallback;
        this.symbol = symbol;
        this.interval = interval;
        this.lookback = lookback;
    }

    @Override
    public TradeAction decide(List<Candle> candles) {

        // If there is not enough data — immediately fall back
        if (candles == null || candles.size() < 5 || ai == null) {
            return fallback.decide(candles);
        }

        try {
            String prompt = buildPrompt(candles);
            // IMPORTANT: use sendPrompt, not ask
            String raw = ai.sendPrompt(prompt);

            if (raw == null) {
                System.out.println("[AI] raw == null, fallback");
                return fallback.decide(candles);
            }

            String result = raw.toUpperCase();

            if (result.contains("BUY"))  return TradeAction.BUY;
            if (result.contains("SELL")) return TradeAction.SELL;

            return TradeAction.HOLD;

        } catch (Exception e) {
            System.err.println("[AI] error: " + e.getMessage());
            return fallback.decide(candles);
        }
    }

    private String buildPrompt(List<Candle> candles) {

        StringBuilder sb = new StringBuilder();
        sb.append("You are a crypto trading assistant.\n");
        sb.append("Pair: ").append(symbol).append(" TF=").append(interval).append("\n");
        sb.append("Last candles:\n");

        int from = Math.max(0, candles.size() - lookback);
        for (int i = from; i < candles.size(); i++) {
            Candle c = candles.get(i);
            sb.append(String.format(
                    "O=%.4f H=%.4f L=%.4f C=%.4f\n",
                    c.open(), c.high(), c.low(), c.close()
            ));
        }

        sb.append("\nReply ONLY with one word: BUY, SELL or HOLD.");
        return sb.toString();
    }

    @Override
    public String getParamsSummary() {
        return "AiStrategy | lookback=" + lookback +
                " | fallback=" + fallback.getClass().getSimpleName();
    }
}