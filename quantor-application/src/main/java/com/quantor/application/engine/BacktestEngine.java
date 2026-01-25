package com.quantor.application.engine;

import com.quantor.domain.ai.RewardShaper;
import com.quantor.domain.ai.TrainLogger;
import com.quantor.domain.market.Candle;
import com.quantor.domain.order.TradeAction;
import com.quantor.domain.risk.RiskManager;
import com.quantor.domain.strategy.Strategy;
import com.quantor.domain.strategy.online.OnlineModel;
import com.quantor.domain.strategy.online.OnlineStrategy;
import com.quantor.domain.trade.Position;
import com.quantor.db.TradeLogger;

import java.util.List;

/**
 * BacktestEngine (offline simulation).
 *
 * Supports:
 *  - virtual balance (cash) + position (assetQty)
 *  - SL/TP via RiskManager
 *  - OnlineStrategy: store entryFeatures on BUY
 *  - OnlineModel training on close (SELL or SL/TP)
 *  - reward shaping: retClose + ret3 + ret5
 *  - training log via TrainLogger (if not null)
 *  - trade log via TradeLogger
 *
 * Constructor is tailored for App.java:
 *  (Strategy, RiskManager, kReward, feeRate, TrainLogger, symbol, interval)
 */
public class BacktestEngine {

    private final Strategy strategy;
    private final RiskManager rm;

    // shaping
    private final double kReward;

    // kept for compatibility with App/signature
    @SuppressWarnings("unused")
    private final double feeRate;

    private final TrainLogger tlog;
    private final String symbol;
    private final String interval;

    // test parameters
    private final double startCash = 1000.0;

    // if a position remains open at the end, force close it and count it as a trade
    private static final boolean FORCE_EXIT_EOD = true;

    public BacktestEngine(Strategy strategy,
                          RiskManager rm,
                          double kReward,
                          double feeRate,
                          TrainLogger tlog,
                          String symbol,
                          String interval) {
        this.strategy = strategy;
        this.rm = rm;
        this.kReward = kReward;
        this.feeRate = feeRate;
        this.tlog = tlog;
        this.symbol = symbol;
        this.interval = interval;
    }

    public void run(List<Candle> candles) {

        if (candles == null || candles.isEmpty()) {
            System.out.println("❌ Backtest: candles empty.");
            return;
        }

        Position pos = new Position();
        double cash = startCash;
        double assetQty = 0.0;

        int trades = 0;
        int wins = 0;

        for (int i = 0; i < candles.size(); i++) {

            List<Candle> hist = candles.subList(0, i + 1);
            double price = candles.get(i).close();

            // ----- 1) SL/TP check -----
            if (pos.isLongOpen() && assetQty > 0.0) {
                double entry = pos.getEntryPrice();

                boolean slHit = rm.hitSL(entry, price);
                boolean tpHit = rm.hitTP(entry, price);

                if (slHit || tpHit) {

                    // Train model on close + log
                    trainIfOnlineWithShaping(pos, candles, i, slHit ? "SL" : "TP", price);

                    // Close the position in the virtual world
                    double gross = assetQty * price;
                    double fee = rm.fee(gross);
                    cash += (gross - fee);

                    double tradePnl = (price - entry) * assetQty - fee; // open fee not accounted
                    trades++;
                    if (tradePnl > 0) wins++;

                    // ✅ trade log (before zeroing qty)
                    TradeLogger.log(
                            "backtest",
                            "N/A",
                            slHit ? "EXIT_SL" : "EXIT_TP",
                            price,
                            assetQty,
                            cash,
                            "Closed by " + (slHit ? "SL" : "TP") + " | pnl=" + String.format("%.6f", tradePnl)
                    );

                    pos.close();
                    assetQty = 0.0;
                }
            }

            // ----- 2) Strategy signal -----
            TradeAction a = strategy.decide(hist);

            // ----- 3) BUY -----
            if (!pos.isLongOpen() && a == TradeAction.BUY) {

                // entryFeatures for OnlineStrategy
                double[] entryFeat = null;
                if (strategy instanceof OnlineStrategy os) {
                    entryFeat = os.extractFeatures(hist);
                }

                double stopPrice = rm.calcStopPrice(price);
                double qty = rm.calcPositionSize(price, stopPrice, cash);

                if (qty > 0.0) {
                    double cost = qty * price;
                    double fee = rm.fee(cost);

                    if (cost + fee <= cash) {
                        cash -= (cost + fee);
                        assetQty = qty;
                        pos.openLong(price, entryFeat);

                        // ✅ BUY log
                        TradeLogger.log(
                                "backtest",
                                "N/A",
                                "BUY",
                                price,
                                qty,
                                cash,
                                "Long entry"
                        );
                    }
                }
            }

            // ----- 4) SELL -----
            if (pos.isLongOpen() && assetQty > 0.0 && a == TradeAction.SELL) {

                // Train model on close + log
                trainIfOnlineWithShaping(pos, candles, i, "SELL", price);

                double entry = pos.getEntryPrice();

                double gross = assetQty * price;
                double fee = rm.fee(gross);
                cash += (gross - fee);

                double tradePnl = (price - entry) * assetQty - fee;
                trades++;
                if (tradePnl > 0) wins++;

                // ✅ SELL log (before zeroing qty)
                TradeLogger.log(
                        "backtest",
                        "N/A",
                        "SELL",
                        price,
                        assetQty,
                        cash,
                        "Exit by SELL | pnl=" + String.format("%.6f", tradePnl)
                );

                pos.close();
                assetQty = 0.0;
            }
        }

        // ----- 5) Final position close (if still open) -----
        double lastPrice = candles.get(candles.size() - 1).close();

        if (FORCE_EXIT_EOD && pos.isLongOpen() && assetQty > 0.0) {
            int exitIndex = candles.size() - 1;
            double exitPrice = lastPrice;

            // train on final close
            trainIfOnlineWithShaping(pos, candles, exitIndex, "EOD", exitPrice);

            double entry = pos.getEntryPrice();
            double gross = assetQty * exitPrice;
            double fee = rm.fee(gross);
            cash += (gross - fee);

            double tradePnl = (exitPrice - entry) * assetQty - fee;
            trades++;
            if (tradePnl > 0) wins++;

            TradeLogger.log(
                    "backtest",
                    "N/A",
                    "FORCE_EXIT_EOD",
                    exitPrice,
                    assetQty,
                    cash,
                    "Forced close at end of test | pnl=" + String.format("%.6f", tradePnl)
            );

            pos.close();
            assetQty = 0.0;
        }

        // final equity
        double equity = cash + assetQty * lastPrice;

        double winrate = trades > 0 ? (wins * 100.0 / trades) : 0.0;

        System.out.println("========== BACKTEST ==========");
        System.out.println("Symbol: " + symbol + "  Interval: " + interval);
        System.out.println("Trades: " + trades);
        System.out.println("Wins: " + wins);
        System.out.println("Winrate: " + String.format("%.2f", winrate) + "%");
        System.out.println("Start cash: " + String.format("%.2f", startCash));
        System.out.println("Final equity: " + String.format("%.2f", equity));
        System.out.println("PnL: " + String.format("%.2f", (equity - startCash)));
    }

    /**
     * Online training on close:
     * - retClose = (exit - entry) / entry
     * - ret3/ret5 based on future prices (i+3, i+5) if available
     * - target01 is produced by RewardShaper
     * - logging to TrainLogger (if present)
     */
    private void trainIfOnlineWithShaping(Position pos,
                                          List<Candle> all,
                                          int exitIndex,
                                          String reason,
                                          double exitPrice) {

        if (!(strategy instanceof OnlineStrategy os)) return;

        double[] feat = pos.getEntryFeatures();
        if (feat == null) return;

        double entry = pos.getEntryPrice();
        if (entry <= 0) return;

        Double p3 = (exitIndex + 3 < all.size()) ? all.get(exitIndex + 3).close() : null;
        Double p5 = (exitIndex + 5 < all.size()) ? all.get(exitIndex + 5).close() : null;

        RewardShaper.Reward r = RewardShaper.build(entry, exitPrice, p3, p5, kReward);

        OnlineModel m = os.getModel();
        double pBefore = m.predictProba(feat);
        m.update(feat, r.target01);
        double pAfter = m.predictProba(feat);

        if (tlog != null) {
            tlog.log(
                    symbol,
                    interval,
                    reason,
                    exitIndex,
                    entry,
                    exitPrice,
                    r.retClose,
                    r.ret3,
                    r.ret5,
                    r.target01,
                    pBefore,
                    pAfter,
                    m.getUpdatesCount()
            );
        }
    }
}