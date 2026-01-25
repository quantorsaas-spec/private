package com.quantor.application.engine;


import com.quantor.application.ports.NotifierPort;
import com.quantor.domain.ai.RewardShaper;
import com.quantor.domain.market.Candle;
import com.quantor.domain.order.TradeAction;
import com.quantor.domain.risk.RiskManager;
import com.quantor.domain.strategy.Strategy;
import com.quantor.domain.strategy.online.OnlineModel;
import com.quantor.domain.strategy.online.OnlineStrategy;
import com.quantor.domain.trade.Position;
import java.util.List;

/**
 * Paper engine (simulation on historical data).
 * IMPORTANT: if the position is still open at the end, we close it at the last price,
 * so the final result is equity, not just cash.
 */
public class PaperEngine {

    private final Strategy strategy;
    private final RiskManager riskManager;
    private final com.quantor.application.ports.NotifierPort notifier;

    private final double initialCash = 1000.0;
    private double cash = initialCash;
    private double assetQty = 0.0;

    private final Position pos = new Position();

    // It is better to take rewardK from OnlineStrategy (as in LiveEngine), but keep a safe fallback:
    private double getRewardKSafe() {
        if (strategy instanceof OnlineStrategy os) return os.getRewardK();
        return 5.0;
    }

    public PaperEngine(Strategy strategy, RiskManager riskManager, com.quantor.application.ports.NotifierPort notifier) {
        this.strategy = strategy;
        this.riskManager = riskManager;
        this.notifier = notifier;
    }

    public void run(List<Candle> candles) {

        if (candles == null || candles.size() < 5) {
            send("⚠ Not enough candles for PAPER.");
            return;
        }

        for (int i = 2; i < candles.size(); i++) {

            List<Candle> history = candles.subList(0, i + 1);
            Candle last = history.get(history.size() - 1);
            double price = last.close();

            // 1) SL/TP if position is open
            if (pos.isLongOpen()) {
                boolean slHit = riskManager.hitSL(pos.getEntryPrice(), price);
                boolean tpHit = riskManager.hitTP(pos.getEntryPrice(), price);

                if (slHit || tpHit) {
                    double notionalClose = assetQty * price;
                    double feeClose = riskManager.fee(notionalClose);
                    double tradePnl = (price - pos.getEntryPrice()) * assetQty - feeClose;

                    trainOnlineOnClose(slHit ? "SL" : "TP", price, i);

                    cash += notionalClose - feeClose;
                    pos.close();
                    assetQty = 0.0;

                    double equity = cash;
                    double pnlFromStart = equity - initialCash;

                    send("🔻 PAPER: closing LONG by " + (slHit ? "StopLoss" : "TakeProfit") +
                            "\nPrice: " + fmt2(price) +
                            "\nTrade PnL: " + fmt6(tradePnl) +
                            "\nEquity: " + fmt6(equity) +
                            "\nPnL from start: " + fmt6(pnlFromStart));
                }
            }

            // 2) tell the strategy whether we are in position
            if (strategy instanceof OnlineStrategy os) {
                os.setInPosition(pos.isLongOpen());
                // In PAPER this is not LIVE; keep false if you want exploration behavior in non-live mode
                os.setLiveMode(false);
            }

            // 3) strategy signal
            TradeAction action = strategy.decide(history);

            // 4) execution
            switch (action) {
                case BUY -> handleBuy(price, history, i);
                case SELL -> handleSell(price, i);
                case HOLD -> logPaper(i, price);
            }
        }

        // ✅ FINAL: if position is open, close it at the last price
        Candle lastCandle = candles.get(candles.size() - 1);
        double lastPrice = lastCandle.close();

        if (pos.isLongOpen() && assetQty > 0.0) {
            double notionalClose = assetQty * lastPrice;
            double feeClose = riskManager.fee(notionalClose);
            double tradePnl = (lastPrice - pos.getEntryPrice()) * assetQty - feeClose;

            trainOnlineOnClose("EOD_CLOSE", lastPrice, candles.size() - 1);

            cash += notionalClose - feeClose;
            pos.close();
            assetQty = 0.0;

            send("🧾 PAPER: forced close at end (EOD_CLOSE)\n" +
                    "Price: " + fmt2(lastPrice) + "\n" +
                    "Trade PnL: " + fmt6(tradePnl) + "\n" +
                    "Cash: " + fmt6(cash));
        }

        double finalEquity = cash; // no open position anymore
        double finalPnl = finalEquity - initialCash;

        send("🧪 PAPER | Simulation finished\n" +
                "Final equity: " + fmt2(finalEquity) + " USDT\n" +
                "PnL: " + fmt2(finalPnl) + " USDT");
    }

    private void handleBuy(double price, List<Candle> history, int i) {
        if (pos.isLongOpen()) { logPaper(i, price); return; }

        double qty = riskManager.positionSize(price);
        if (qty <= 0) { logPaper(i, price); return; }

        double notional = qty * price;
        double feeOpen = riskManager.fee(notional);
        double totalCost = notional + feeOpen;

        if (totalCost > cash) { logPaper(i, price); return; }

        double[] entryFeatures = null;
        if (strategy instanceof OnlineStrategy os) {
            entryFeatures = os.extractFeatures(history);
        }

        cash -= totalCost;
        assetQty += qty;

        // ✅ IMPORTANT: store qty and feeOpenUSDT, same as in LiveEngine
        pos.openLong(price, entryFeatures, qty, feeOpen);

        logPaper(i, price);
    }

    private void handleSell(double price, int i) {
        if (!pos.isLongOpen() || assetQty <= 0.0) { logPaper(i, price); return; }

        double notionalClose = assetQty * price;
        double feeClose = riskManager.fee(notionalClose);
        double tradePnl = (price - pos.getEntryPrice()) * assetQty - feeClose;

        trainOnlineOnClose("SELL", price, i);

        cash += notionalClose - feeClose;
        pos.close();
        assetQty = 0.0;

        logPaper(i, price);
    }

    private void trainOnlineOnClose(String reason, double exitPrice, int i) {
        if (!(strategy instanceof OnlineStrategy os)) return;

        try {
            double[] feat = pos.getEntryFeatures();
            if (feat == null) return;

            double entry = pos.getEntryPrice();
            if (entry <= 0) return;

            double qty = pos.getEntryQty();
            double feeOpenUSDT = pos.getFeeOpenUSDT();
            if (qty <= 0.0) qty = 1.0;

            double entryNotional = qty * entry;
            double exitNotional  = qty * exitPrice;

            double feeOpenPct  = entryNotional > 0 ? (feeOpenUSDT / entryNotional) : 0.0;
            double feeClosePct = exitNotional > 0 ? (riskManager.fee(exitNotional) / exitNotional) : 0.0;

            double rewardK = getRewardKSafe();

            RewardShaper.Reward r = RewardShaper.build(
                    entry, exitPrice,
                    null, null,
                    rewardK,
                    feeOpenPct, feeClosePct
            );

            // ✅ Same as LiveEngine: stronger target based on retClose
            double raw = r.retClose;
            double y = 0.5 + 0.5 * Math.tanh(rewardK * raw * 5.0);
            y = Math.max(0.05, Math.min(0.95, y));

            OnlineModel m = os.getModel();
            double pBefore = m.predictProba(feat);
            m.update(feat, y);
            double pAfter = m.predictProba(feat);

            System.out.println("[PaperTrain] i=" + i +
                    " reason=" + reason +
                    " y=" + fmt4(y) +
                    " pBefore=" + fmt6(pBefore) +
                    " pAfter=" + fmt6(pAfter) +
                    " updates=" + m.getUpdatesCount());

        } catch (Exception e) {
            System.err.println("[PaperTrain] error: " + e.getMessage());
        }
    }

    private void logPaper(int i, double price) {
        String posState = pos.isLongOpen() ? "LONG" : "NONE";
        System.out.println("[Paper] i=" + i +
                " price=" + fmt2(price) +
                " cash=" + fmt2(cash) +
                " asset=" + fmt6(assetQty) +
                " pos=" + posState);
    }

    private void send(String text) {
        if (notifier != null) notifier.send(text);
        else System.out.println(text);
    }

    private static String fmt2(double v) { return String.format(java.util.Locale.US, "%.2f", v); }
    private static String fmt4(double v) { return String.format(java.util.Locale.US, "%.4f", v); }
    private static String fmt6(double v) { return String.format(java.util.Locale.US, "%.6f", v); }
}