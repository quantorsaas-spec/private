package com.quantor.application.engine;


import com.quantor.application.ports.NotifierPort;
import com.quantor.application.lifecycle.BotState;
import com.quantor.application.lifecycle.BotStateManager;
import com.quantor.domain.ai.AiStatsTracker;
import com.quantor.domain.ai.RewardShaper;
import com.quantor.domain.ai.TrainLogger;
import com.quantor.domain.market.Candle;
import com.quantor.domain.order.TradeAction;
import com.quantor.domain.risk.RiskManager;
import com.quantor.domain.strategy.AutoTuner;
import com.quantor.domain.strategy.Strategy;
import com.quantor.domain.strategy.online.OnlineModel;
import com.quantor.domain.strategy.online.OnlineStrategy;
import com.quantor.domain.trade.Position;
import java.util.List;

public class LiveEngine implements Runnable {

    private final TrainLogger trainLogger;

    // Cooldown
    private static final int MIN_TICKS_BETWEEN_TRADES_TRAIN = 20;
    private static final int MIN_TICKS_BETWEEN_TRADES_LIVE  = 6;
    private int lastTradeTick = -999999;

    private BotState lastLoggedState = null;
    private int lastHeartbeatTick = 0;
    private static final int HEARTBEAT_EVERY_TICKS = 20;

    private final BotStateManager stateManager;
    private final Strategy strategy;
    private final RiskManager riskManager;
    private final com.quantor.application.exchange.ExchangePort exchange;

    private final com.quantor.application.exchange.MarketSymbol symbol;
    private final com.quantor.application.exchange.Timeframe timeframe;
    private final com.quantor.application.ports.NotifierPort notifier;

    private boolean realTradingEnabled;

    private final AiStatsTracker stats;
    private final AutoTuner autoTuner;

    private final int lookback = 200;

    private final double initialCash = 1000.0;
    private double cash = initialCash;
    private double assetQty = 0.0;
    private final Position pos = new Position();

    private final double maxSessionDrawdownPct;
    private final double disableRealTradingDrawdownPct;

    private double peakEquity = initialCash;
    private boolean sessionStoppedByDrawdown = false;

    private volatile boolean trainingMode = false;
    private int currentTick = 0;

    private static final int HOLD_NOTIFY_EVERY_TICKS_LIVE = 12;
    private static final int HOLD_NOTIFY_EVERY_TICKS_TRAIN = 60;

    private static final int TRAIN_MAX_TICKS_IN_POSITION = 30;
    private int ticksInPosition = 0;

    private TradeAction lastAction = null;
    private boolean lastLongOpen = false;

    private final boolean debugOnline = true;
    private final boolean debugCandles = false;

    private final boolean useClosedCandlesOnly = true;

    // closed-candle dedup
    private long lastProcessedCloseTimeMs = -1L;
    private String lastProcessedFingerprint = null;

    public boolean isTrainingMode() { return trainingMode; }

    public LiveEngine(Strategy strategy,
                      RiskManager riskManager,
                      com.quantor.application.exchange.ExchangePort exchange,
                      com.quantor.application.exchange.MarketSymbol symbol,
                      com.quantor.application.exchange.Timeframe timeframe,
                      com.quantor.application.ports.NotifierPort notifier,
                      BotStateManager stateManager,
                      boolean realTradingEnabled,
                      AiStatsTracker stats,
                      double maxSessionDrawdownPct,
                      double disableRealTradingDrawdownPct,
                      AutoTuner autoTuner) {

        this.strategy = strategy;
        this.riskManager = riskManager;
        this.exchange = exchange;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.notifier = notifier;
        this.stateManager = stateManager;
        this.realTradingEnabled = realTradingEnabled;
        this.stats = stats;
        this.maxSessionDrawdownPct = maxSessionDrawdownPct;
        this.disableRealTradingDrawdownPct = disableRealTradingDrawdownPct;
        this.peakEquity = initialCash;
        this.autoTuner = autoTuner;

        TrainLogger tlog = null;
        try {
            if (strategy instanceof OnlineStrategy) {
                tlog = new TrainLogger("data/online_train_log.csv");
            }
        } catch (Exception ignored) {}
        this.trainLogger = tlog;
    }

    public void setTrainingMode(boolean trainingMode) {
        this.trainingMode = trainingMode;
    }

    private void logConsole(String msg) {
        System.out.println("[ENGINE] " + msg);
    }

    private void notifyTg(String msg) {
        if (notifier != null) notifier.send(msg);
        else logConsole(msg);
    }

    public void stop() { stateManager.stop(); }
    public void pause() { stateManager.pause(); }
    public void resumeLive() { stateManager.start(); }

    private List<Candle> prepareClosedCandles(List<Candle> raw) {
        if (!useClosedCandlesOnly) return raw;
        if (raw == null) return null;
        if (raw.size() <= 2) return raw;
        return raw.subList(0, raw.size() - 1);
    }

    private String fp(Candle c) {
        return c.open() + "|" + c.high() + "|" + c.low() + "|" + c.close();
    }

    @Override
    public void run() {

        // Human-friendly label for logs/notifications.
        final String interval = (timeframe == null) ? "?" : timeframe.name();

        String modeLabel = trainingMode ? "TRAIN" : "LIVE";

        notifyTg("🟢 " + modeLabel + " mode started for " + symbol + " (" + interval + ")\n" +
                "Commands: /pause, /resume, /stop\n" +
                "Trading mode: " + (realTradingEnabled
                ? "TESTNET (with real orders)"
                : "virtual trading (no real orders)."));

        stateManager.start();
        peakEquity = initialCash;
        sessionStoppedByDrawdown = false;
        currentTick = 0;
        lastAction = null;
        lastLongOpen = false;
        ticksInPosition = 0;

        lastProcessedCloseTimeMs = -1L;
        lastProcessedFingerprint = null;

        boolean wasPaused = false;

        while (true) {
            try {
                BotState state = stateManager.getState();

                if (lastLoggedState != state) {
                    logConsole(modeLabel + " state changed -> " + state + " (tick=" + currentTick + ")");
                    lastLoggedState = state;
                } else if (currentTick - lastHeartbeatTick >= HEARTBEAT_EVERY_TICKS) {
                    logConsole(modeLabel + " heartbeat tick=" + currentTick);
                    lastHeartbeatTick = currentTick;
                }

                if (state == BotState.STOPPED) {
                    notifyTg("🔴 " + modeLabel + " stopped by STOP command");
                    break;
                }

                if (state == BotState.PAUSED) {
                    if (!wasPaused) {
                        notifyTg("⏸ " + modeLabel + " paused");
                        wasPaused = true;
                    }
                    Thread.sleep(2000);
                    continue;
                }

                if (wasPaused) {
                    notifyTg("▶ " + modeLabel + " resumed");
                    wasPaused = false;
                }

                currentTick++;

                List<Candle> candlesRaw;
                try {
                    candlesRaw = exchange.getCandles(symbol, timeframe, lookback);
                } catch (Exception ex) {
                    notifyTg("⚠ Failed to fetch candles for " + symbol + ": " + ex.getMessage());
                    ex.printStackTrace();
                    Thread.sleep(5000);
                    continue;
                }

                if (candlesRaw == null || candlesRaw.isEmpty()) {
                    notifyTg("⚠ Binance returned an empty candle list for " + symbol);
                    Thread.sleep(5000);
                    continue;
                }

                List<Candle> candles = prepareClosedCandles(candlesRaw);

                if (candles == null || candles.size() < 2) {
                    notifyTg("⚠ Not enough candles (after closed-candle filtering) for " + symbol);
                    Thread.sleep(5000);
                    continue;
                }

                Candle lastClosed = candles.get(candles.size() - 1);
                double price = lastClosed.close();

                // "new closed candle" dedup
                long ct = lastClosed.closeTimeMs();
                if (ct > 0) {
                    if (ct == lastProcessedCloseTimeMs) {
                        if (debugCandles) logConsole("DBG skip: same closeTimeMs=" + ct);
                        Thread.sleep(2000);
                        continue;
                    }
                    lastProcessedCloseTimeMs = ct;
                } else {
                    String f = fp(lastClosed);
                    if (f.equals(lastProcessedFingerprint)) {
                        if (debugCandles) logConsole("DBG skip: same fingerprint");
                        Thread.sleep(2000);
                        continue;
                    }
                    lastProcessedFingerprint = f;
                }

                if (pos.isLongOpen()) ticksInPosition++;
                else ticksInPosition = 0;

                // TRAIN time-exit
                if (trainingMode && pos.isLongOpen() && ticksInPosition >= TRAIN_MAX_TICKS_IN_POSITION) {
                    String baseMsg = "⏱ TRAIN TIME_EXIT\n" +
                            "📡 [" + currentTick + "] " + symbol + " " + interval +
                            " | close=" + price;

                    handleSell(price, baseMsg, "TIME_EXIT");
                    ticksInPosition = 0;
                    Thread.sleep(2000);
                    continue;
                }

                // SL/TP auto-close
                if (pos.isLongOpen()) {
                    boolean slHit = riskManager.hitSL(pos.getEntryPrice(), price);
                    boolean tpHit = riskManager.hitTP(pos.getEntryPrice(), price);

                    if (slHit || tpHit) {
                        String reason = slHit ? "SL" : "TP";
                        String baseMsg = (slHit ? "🔻 StopLoss" : "✅ TakeProfit") + "\n" +
                                "📡 [" + currentTick + "] " + symbol + " " + interval +
                                " | close=" + price;

                        handleSell(price, baseMsg, reason);
                        Thread.sleep(2000);
                        continue;
                    }
                }

                if (strategy instanceof OnlineStrategy os) {
                    os.setInPosition(pos.isLongOpen());
                    os.setLiveMode(!trainingMode);
                }

                TradeAction action;
                try {
                    action = strategy.decide(candles);
                } catch (Exception ex) {
                    notifyTg("❌ Strategy error: " + ex.getMessage());
                    ex.printStackTrace();
                    Thread.sleep(5000);
                    continue;
                }

                if (action == TradeAction.BUY && pos.isLongOpen()) {
                    if (debugOnline) System.out.println("[LIVE] BUY suppressed: LONG already open");
                    action = TradeAction.HOLD;
                }

                String onlineDbg = "";
                if (debugOnline && strategy instanceof OnlineStrategy os) {
                    onlineDbg = buildOnlineDbg(os, candles);
                }

                // cooldown
                int minTicksBetween = trainingMode ? MIN_TICKS_BETWEEN_TRADES_TRAIN : MIN_TICKS_BETWEEN_TRADES_LIVE;
                boolean cooldownActive = (currentTick - lastTradeTick) < minTicksBetween;

                if (cooldownActive && (action == TradeAction.BUY || action == TradeAction.SELL)) {
                    if (debugOnline) {
                        System.out.println("[LIVE] cooldown: suppressed " + action +
                                " (dticks=" + (currentTick - lastTradeTick) + "/" + minTicksBetween + ")");
                    }
                    action = TradeAction.HOLD;
                }

                String baseMsg = "📡 [" + currentTick + "] " + symbol + " " + interval +
                        " | close=" + price +
                        " | signal: " + action +
                        onlineDbg;

                switch (action) {
                    case BUY -> handleBuy(price, baseMsg, candles);
                    case SELL -> handleSell(price, baseMsg, "SELL");
                    case HOLD -> maybeNotifyHold(baseMsg, price, action);
                    default -> maybeNotifyHold(baseMsg + "\n❔ Unknown signal, no trades.", price, action);
                }

                lastLongOpen = pos.isLongOpen();
                lastAction = action;

                double equityNow = cash + assetQty * price;
                checkRiskGuards(equityNow);

                Thread.sleep(5000);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                notifyTg("⚠ " + (trainingMode ? "TRAIN" : "LIVE") + " interrupted: " + ie.getMessage());
                break;
            } catch (Throwable e) {
                notifyTg("❌ Loop error in " + (trainingMode ? "TRAIN" : "LIVE") + ": " +
                        e.getClass().getSimpleName() + " " + e.getMessage());
                e.printStackTrace();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        notifyTg("🟡 " + (trainingMode ? "TRAIN" : "LIVE") + " mode stopped for " + symbol);
        logConsole("thread finished.");
    }

    // ✅ TRAIN ONLY ON CLOSE
    private void trainOnlineOnClose(String reason, double exitPrice) {
        if (!(strategy instanceof OnlineStrategy os)) return;

        try {
            // For logging / diagnostics
            String interval = timeframe != null ? timeframe.name() : "";
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

            RewardShaper.Reward r = RewardShaper.build(
                    entry, exitPrice,
                    null, null,
                    os.getRewardK(),
                    feeOpenPct, feeClosePct
            );

            // ✅ amplify target: make it more "two-class", but not too extreme
            double raw = r.retClose;                 // close return (you have it)
            double k = os.getRewardK();              // will be 12.0 from config
            double y = 0.5 + 0.5 * Math.tanh(k * raw * 5.0);  // *5 amplifies the reaction

            // clamp away from extremes (more stable)
            y = Math.max(0.05, Math.min(0.95, y));

            OnlineModel m = os.getModel();
            double pBefore = m.predictProba(feat);
            m.update(feat, y);
            double pAfter = m.predictProba(feat);

            if (trainLogger != null) {
                trainLogger.log(
                        symbol.asBaseQuote(), interval,
                        reason,
                        currentTick,
                        entry, exitPrice,
                        r.retClose, r.ret3, r.ret5,
                        r.target01,
                        pBefore, pAfter,
                        m.getUpdatesCount()
                );
            }
        } catch (Exception e) {
            System.err.println("[LIVE] trainOnlineOnClose error: " + e.getMessage());
        }
    }

    private void maybeNotifyHold(String baseMsg, double price, TradeAction action) {
        int every = trainingMode ? HOLD_NOTIFY_EVERY_TICKS_TRAIN : HOLD_NOTIFY_EVERY_TICKS_LIVE;

        boolean timeToSend = (currentTick % every == 0);
        boolean actionChanged = (lastAction != null && lastAction != action);
        boolean posChanged = (lastLongOpen != pos.isLongOpen());

        if (!timeToSend && !actionChanged && !posChanged) return;

        double equity = cash + assetQty * price;
        double pnl = equity - initialCash;

        notifyTg(baseMsg + "\n😴 Action: HOLD\n" +
                "Equity: " + fmt6(equity) + " USDT\n" +
                "PnL since session start: " + fmt6(pnl) + " USDT");
    }

    private String buildOnlineDbg(OnlineStrategy os, List<Candle> candles) {
        try {
            OnlineModel m = os.getModel();
            long updates = m.getUpdatesCount();

            double p = Double.NaN;
            double[] feat = os.extractFeatures(candles);
            if (feat != null) p = m.predictProba(feat);

            double thr = os.getThreshold();
            double upper = 0.5 + thr;
            double lower = 0.5 - thr;

            return "\n🧠 OnlineDBG: updates=" + updates +
                    (Double.isNaN(p) ? "" : (" | p=" + fmt6(p))) +
                    " | upper=" + fmt6(upper) +
                    " | lower=" + fmt6(lower);

        } catch (Exception e) {
            return "\n🧠 OnlineDBG: error (" + e.getMessage() + ")";
        }
    }

    // ---------------------------------------------------------
    // BUY / SELL
    // ---------------------------------------------------------

    private void handleBuy(double price, String baseMsg, List<Candle> candles) {
        if (pos.isLongOpen()) {
            maybeNotifyHold(baseMsg + "\n⚠ BUY, but a LONG is already open.", price, TradeAction.HOLD);
            return;
        }

        double qty = riskManager.positionSize(price);
        if (qty <= 0) {
            double equity = cash + assetQty * price;
            double pnl = equity - initialCash;
            notifyTg(baseMsg + "\n⚠ qty <= 0. Trade skipped.\n" +
                    "Equity: " + fmt6(equity) + " USDT\n" +
                    "PnL: " + fmt6(pnl) + " USDT");
            return;
        }

        double notional = qty * price;
        double fee = riskManager.fee(notional);
        double totalCost = notional + fee;

        if (totalCost > cash) {
            double equity = cash + assetQty * price;
            double pnl = equity - initialCash;
            notifyTg(baseMsg + "\n⚠ Not enough cash to buy.\n" +
                    "Needed: " + fmt6(totalCost) + ", available: " + fmt6(cash) + "\n" +
                    "Equity: " + fmt6(equity) + "\n" +
                    "PnL: " + fmt6(pnl));
            return;
        }

        // features
        double[] entryFeatures = null;
        if (strategy instanceof OnlineStrategy os) {
            entryFeatures = os.extractFeatures(candles);
        }

        // TESTNET BUY first
        if (realTradingEnabled) {
            try {
                exchange.marketBuy(symbol, qty);
                notifyTg("🧪 [TESTNET BUY] " + symbol + " qty=" + fmt8(qty));
            } catch (Exception ex) {
                notifyTg("⛔ TESTNET BUY failed: " + ex.getMessage() + "\nTrade cancelled.");
                return;
            }
        }

        // virtual open
        cash -= totalCost;
        assetQty += qty;
        pos.openLong(price, entryFeatures, qty, fee);

        ticksInPosition = 0;
        lastTradeTick = currentTick;

        double equity = cash + assetQty * price;
        double pnl = equity - initialCash;

        notifyTg(baseMsg + "\n💡 BUY\n" +
                "Qty: " + fmt8(qty) + "\n" +
                "Notional: " + fmt6(notional) + "\n" +
                "Fee(open): " + fmt6(fee) + "\n" +
                "Cash: " + fmt6(cash) + "\n" +
                "Equity: " + fmt6(equity) + "\n" +
                "PnL: " + fmt6(pnl));
    }

    private void handleSell(double price, String baseMsg, String reason) {
        if (!pos.isLongOpen() || assetQty <= 0.0) return;

        double qtyToSell = assetQty;

        double notionalClose = qtyToSell * price;
        double feeClose = riskManager.fee(notionalClose);
        double tradePnl = (price - pos.getEntryPrice()) * qtyToSell - feeClose;

        // ✅ train on close
        trainOnlineOnClose(reason, price);

        // TESTNET SELL BEFORE zeroing assetQty
        if (realTradingEnabled) {
            try {
                exchange.marketSell(symbol, qtyToSell);
                notifyTg("🧪 [TESTNET SELL] " + symbol + " qty=" + fmt8(qtyToSell));
            } catch (Exception ex) {
                notifyTg("⚠ TESTNET SELL error: " + ex.getMessage());
            }
        }

        cash += notionalClose - feeClose;
        pos.close();
        assetQty = 0.0;
        ticksInPosition = 0;

        lastTradeTick = currentTick;

        double equity = cash;
        double pnlFromStart = equity - initialCash;

        try {
            if (stats != null) stats.onTradeClosed(tradePnl, equity);
            if (autoTuner != null) autoTuner.onTrade(tradePnl);
        } catch (Exception e) {
            System.err.println("[LIVE] stats/autoTuner error: " + e.getMessage());
        }

        notifyTg(baseMsg + "\n💡 " + reason + "\n" +
                "Notional(close): " + fmt6(notionalClose) + "\n" +
                "Fee(close): " + fmt6(feeClose) + "\n" +
                "Cash: " + fmt6(cash) + "\n" +
                "PnL(trade): " + fmt6(tradePnl) + "\n" +
                "Equity: " + fmt6(equity) + "\n" +
                "PnL(from start): " + fmt6(pnlFromStart));

        checkRiskGuards(equity);
    }

    private void checkRiskGuards(double equity) {
        if (trainingMode) return;

        if (equity > peakEquity) peakEquity = equity;
        if (stats != null) stats.updatePeak(equity);

        double dd = peakEquity - equity;
        double ddPct = peakEquity > 0 ? dd / peakEquity : 0.0;

        if (!sessionStoppedByDrawdown &&
                maxSessionDrawdownPct > 0 &&
                ddPct >= maxSessionDrawdownPct) {

            sessionStoppedByDrawdown = true;
            notifyTg("⛔ Max session DD: " +
                    fmt2(ddPct * 100) + "% (limit " +
                    fmt2(maxSessionDrawdownPct * 100) + "%). STOP.");
            stateManager.stop();
            return;
        }

        if (realTradingEnabled && stats != null && disableRealTradingDrawdownPct > 0) {
            double globalDdPct = stats.getDrawdown(equity);

            if (globalDdPct >= disableRealTradingDrawdownPct) {
                realTradingEnabled = false;
                notifyTg("⚠ TESTNET realTrading disabled due to DD: " +
                        fmt2(globalDdPct * 100) + "% (limit " +
                        fmt2(disableRealTradingDrawdownPct * 100) + "%).");
            }
        }
    }

    private static String fmt2(double v) { return String.format(java.util.Locale.US, "%.2f", v); }
    private static String fmt6(double v) { return String.format(java.util.Locale.US, "%.6f", v); }
    private static String fmt8(double v) { return String.format(java.util.Locale.US, "%.8f", v); }
}