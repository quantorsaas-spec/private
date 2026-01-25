package com.quantor.domain.strategy;


import com.quantor.domain.ai.AiStatsTracker;
import com.quantor.domain.trade.Position;
/**
 * A simple strategy "tuner".
 *
 * It does not perform real ML, but:
 *   - reads statistics from AiStatsTracker,
 *   - based on winrate, maxDrawdown, and average PnL
 *     suggests small parameter adjustments:
 *       fastEma, slowEma, stopLossPct, takeProfitPct, positionUSDT.
 *
 * Usage:
 *   StrategyTuner tuner = new StrategyTuner(aiStatsTracker);
 *   TunedParams suggestion = tuner.suggest(currentParams);
 *   String explanation = tuner.buildExplanation(currentParams, suggestion);
 *   // send explanation to Telegram
 */
public class StrategyTuner {

    private final AiStatsTracker stats;

    public StrategyTuner(AiStatsTracker stats) {
        this.stats = stats;
    }

    /**
     * Returns recommended parameters based on trading statistics.
     * If there is not enough data, returns current as-is.
     */
    public TunedParams suggest(TunedParams current) {
        int total = stats.getTotalTrades();
        if (total < 20) {
            // Not enough statistics — do not change anything.
            return current;
        }

        double winrate = stats.getWinrate();       // 0..1
        double avgPnl = stats.getAvgPnl();         // average trade PnL (in USDT)
        double maxDD  = stats.getMaxDrawdown();    // max drawdown in USDT

        int fast = current.fastEma();
        int slow = current.slowEma();
        double sl = current.stopLossPct();
        double tp = current.takeProfitPct();
        double pos = current.positionUSDT();

        // Base deposit to convert maxDD into a percentage.
        // In LiveEngine you have initialCash = 1000.0, use the same value here.
        double baseDeposit = 1000.0;
        double ddPct = maxDD / baseDeposit;

        // --- 1. If winrate is low, reduce risk and make signals less frequent ---
        if (winrate < 0.45) {
            // Slightly slower EMAs (filter noise)
            fast = Math.min(fast + 2, 30);
            slow = Math.min(slow + 4, 80);

            // Tighter stop-loss
            sl *= 0.8;           // -20%

            // Reduce position size
            pos *= 0.7;
        }

        // --- 2. If winrate is good and avg PnL > 0, we can be slightly more aggressive ---
        if (winrate > 0.6 && avgPnl > 0) {
            // Slightly increase TP
            tp *= 1.2;

            // Increase position size very carefully
            pos *= 1.1;
        }

        // --- 3. Protection against large drawdowns ---
        if (ddPct > 0.10) { // drawdown > 10%
            sl *= 0.8;      // tighten stop-loss further
            pos *= 0.5;     // significantly reduce position size
        }

        // --- 4. Range limits (safety guards) ---

        // EMA
        if (fast < 3) fast = 3;
        if (fast > 50) fast = 50;

        if (slow <= fast) slow = fast + 3;
        if (slow > 200) slow = 200;

        // Stop-loss
        if (sl < 0.001) sl = 0.001;   // 0.1%
        if (sl > 0.05)  sl = 0.05;    // 5%

        // Take-profit
        if (tp < sl * 1.2) {
            // TP should be at least slightly larger than SL
            tp = sl * 1.5;
        }
        if (tp > 0.1) tp = 0.1;       // 10%

        // Position size
        if (pos < 5)   pos = 5;
        if (pos > 200) pos = 200;

        return new TunedParams(fast, slow, sl, tp, pos);
    }

    /**
     * Builds a Telegram-friendly text explaining what is suggested and why.
     */
    public String buildExplanation(TunedParams current, TunedParams suggested) {
        StringBuilder sb = new StringBuilder();

        sb.append("📊 AI strategy tuning based on real trades\n\n");
        sb.append("Statistics:\n");
        sb.append("Total trades: ").append(stats.getTotalTrades()).append("\n");
        sb.append("Winrate: ").append(String.format("%.2f%%", stats.getWinrate() * 100)).append("\n");
        sb.append("Average PnL: ").append(String.format("%.4f", stats.getAvgPnl())).append(" USDT\n");
        sb.append("Max drawdown: ").append(String.format("%.4f", stats.getMaxDrawdown())).append(" USDT\n\n");

        sb.append("Current parameters:\n");
        sb.append("fastEma = ").append(current.fastEma()).append("\n");
        sb.append("slowEma = ").append(current.slowEma()).append("\n");
        sb.append("stopLossPct = ").append(String.format("%.4f", current.stopLossPct())).append("\n");
        sb.append("takeProfitPct = ").append(String.format("%.4f", current.takeProfitPct())).append("\n");
        sb.append("positionUSDT = ").append(String.format("%.2f", current.positionUSDT())).append("\n\n");

        if (current.equals(suggested)) {
            sb.append("✅ No changes are needed based on the current data.\n");
        } else {
            sb.append("Recommended parameters:\n");
            sb.append("fastEma = ").append(suggested.fastEma()).append("\n");
            sb.append("slowEma = ").append(suggested.slowEma()).append("\n");
            sb.append("stopLossPct = ").append(String.format("%.4f", suggested.stopLossPct())).append("\n");
            sb.append("takeProfitPct = ").append(String.format("%.4f", suggested.takeProfitPct())).append("\n");
            sb.append("positionUSDT = ").append(String.format("%.2f", suggested.positionUSDT())).append("\n\n");

            sb.append("⚠ How to apply:\n");
            sb.append("Update fastEma/slowEma/stopLossPct/takeProfitPct/positionUSDT\n");
            sb.append("in config.properties and restart the bot.\n");
        }

        return sb.toString();
    }
}