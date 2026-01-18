package com.quantor.infrastructure.ai;

import com.quantor.domain.market.Candle;
import com.quantor.domain.order.TradeAction;
import com.quantor.domain.strategy.Strategy;
import com.quantor.domain.strategy.impl.EmaCrossStrategy;
import com.quantor.exchange.ChatGptClient;

import java.util.List;

/**
 * Hybrid strategy:
 *  - baseStrategy (usually EMA) generates BUY / SELL / HOLD
 *  - AI only filters BUY / SELL signals and agrees by default.
 *
 * Logic:
 *  1) base = HOLD → return HOLD immediately (AI is not called).
 *  2) base = BUY / SELL:
 *      - ask AI whether the entry looks reasonable.
 *      - if AI answers ALLOW / YES → keep BUY / SELL.
 *      - if AI answers BLOCK / NO → convert to HOLD.
 *  3) If AI fails → fall back to the original baseStrategy signal.
 */
public class HybridEmaAiFilterStrategy implements Strategy {

    private final Strategy baseStrategy;    // e.g. EmaCrossStrategy
    private final ChatGptClient gpt;
    private final String symbol;
    private final String interval;
    private final int lookback;             // number of candles shown to AI

    public HybridEmaAiFilterStrategy(Strategy baseStrategy,
                                     ChatGptClient gpt,
                                     String symbol,
                                     String interval,
                                     int lookback) {
        this.baseStrategy = baseStrategy;
        this.gpt = gpt;
        this.symbol = symbol;
        this.interval = interval;
        this.lookback = lookback;
    }

    @Override
    public TradeAction decide(List<Candle> candles) {

        // 1) Let EMA (or another base strategy) decide first
        TradeAction baseAction = baseStrategy.decide(candles);

        // 2) If HOLD — do not call AI
        if (baseAction == TradeAction.HOLD || gpt == null) {
            return baseAction;
        }

        try {
            if (candles == null || candles.isEmpty()) {
                return TradeAction.HOLD;
            }

            int n = Math.min(lookback, candles.size());
            List<Candle> sub = candles.subList(candles.size() - n, candles.size());

            double lastClose = sub.get(sub.size() - 1).close();
            double prevClose = sub.get(sub.size() - 2).close();
            double changePct = (lastClose - prevClose) / prevClose * 100.0;

            StringBuilder sb = new StringBuilder();
            for (Candle c : sub) {
                sb.append(String.format(
                        "o=%.2f h=%.2f l=%.2f c=%.2f\n",
                        c.open(), c.high(), c.low(), c.close()
                ));
            }

            String prompt = """
                    You are a risk filter for an automated trading bot.

                    Instrument: %s
                    Timeframe: %s
                    Base strategy signal: %s

                    Last %d candles (one per line, format: o=.. h=.. l=.. c=..):
                    %s

                    Extra features:
                    - lastClose = %.2f
                    - prevClose = %.2f
                    - changePct = %.4f%% (last vs previous close)

                    Task:
                    - Decide if this %s signal is reasonable or too risky.
                    - If the signal looks OK -> answer ALLOW.
                    - If the signal looks dangerous, random or unclear -> answer BLOCK.

                    Answer with ONE WORD ONLY (uppercase): ALLOW or BLOCK.
                    """.formatted(
                    symbol,
                    interval,
                    baseAction,
                    n,
                    sb,
                    lastClose, prevClose, changePct,
                    baseAction
            );

            String raw = gpt.sendPrompt(prompt);
            if (raw == null) {
                System.out.println("[Hybrid] AI raw == null, use baseAction = " + baseAction);
                return baseAction;
            }

            System.out.println("[Hybrid AI RAW] " + raw);
            String upper = raw.toUpperCase();

            // 3) Parse AI response
            if (upper.contains("ALLOW") || upper.contains("YES")) {
                // AI agrees → trade using the base signal
                return baseAction;
            }

            if (upper.contains("BLOCK") || upper.contains("NO")) {
                // AI disagrees → block entry and convert to HOLD
                System.out.println("[Hybrid] AI BLOCK " + baseAction + " → HOLD");
                return TradeAction.HOLD;
            }

            // Unclear response: safety fallback — follow base strategy
            System.out.println("[Hybrid] Unknown AI answer, fallback to baseAction = " + baseAction);
            return baseAction;

        } catch (Exception e) {
            System.out.println("[Hybrid] AI error → use baseAction (" + baseAction + "): " + e.getMessage());
            return baseAction;
        }
    }

    public String getParamsSummary() {
        return "HybridEmaAiFilter(base=" + baseStrategy.getClass().getSimpleName() +
                ", lookback=" + lookback + ")";
    }
}