package com.quantor.domain.strategy.online;



import com.quantor.domain.market.Candle;
import com.quantor.domain.order.TradeAction;
import com.quantor.domain.strategy.Strategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class OnlineStrategy implements Strategy {

    private final double minRetAbs;
    private final double minMomentumToVol;
    private final double minBuyEdgeAbs;

    private final OnlineModel model;
    private final int lookback;

    private final double threshold;
    private final double featureScale;
    private final boolean debugProba;

    private final double rewardK;

    private final double minEdge;
    private final double minVol;

    private final double buyMargin;

    private final boolean explorationEnabled;
    private final int forceBuyEvery;

    private volatile boolean isLiveMode;
    private volatile boolean inPosition = false;

    private int flatHoldCounterExploration = 0;

    private final String modelPath;

    public OnlineStrategy(Properties cfg) {

        this.minRetAbs = Double.parseDouble(cfg.getProperty("onlineMinRetAbs", "0.00045"));
        this.minMomentumToVol = Double.parseDouble(cfg.getProperty("onlineMinMomentumToVol", "2.0"));
        this.minBuyEdgeAbs = Double.parseDouble(cfg.getProperty("onlineMinBuyEdgeAbs", "0.0010"));

        this.lookback = Integer.parseInt(cfg.getProperty("onlineLookback", "20"));

        double learningRate = Double.parseDouble(cfg.getProperty("onlineLearningRate", "0.1"));
        double l2 = Double.parseDouble(cfg.getProperty("onlineL2", "0.0"));

        this.threshold = Double.parseDouble(cfg.getProperty("onlineThreshold", "0.02"));

        this.modelPath = cfg.getProperty("onlineModelPath", "data/online_model.csv");
        boolean persistenceEnabled = Boolean.parseBoolean(cfg.getProperty("onlineModelPersistenceEnabled", "true"));
        int saveEvery = Integer.parseInt(cfg.getProperty("onlineModelSaveEvery", "50"));

        this.featureScale = Double.parseDouble(cfg.getProperty("onlineFeatureScale", "1.0"));
        this.debugProba = Boolean.parseBoolean(cfg.getProperty("onlineDebugProba", "false"));

        this.rewardK = Double.parseDouble(cfg.getProperty("onlineRewardK", "5.0"));

        this.minEdge = Double.parseDouble(cfg.getProperty("onlineMinEdge", "0.03"));
        this.minVol  = Double.parseDouble(cfg.getProperty("onlineMinVol",  "0.001"));

        this.buyMargin = Double.parseDouble(cfg.getProperty("onlineBuyMargin", "0.0"));

        this.explorationEnabled = Boolean.parseBoolean(cfg.getProperty("onlineExplorationEnabled", "false"));
        this.forceBuyEvery = Integer.parseInt(cfg.getProperty("onlineExplorationForceBuyEvery", "10"));

        String mode = cfg.getProperty("mode", "TEST").trim().toUpperCase();
        this.isLiveMode = mode.equals("LIVE");

        int featureSize = this.lookback + 3;

        this.model = new OnlineModel(
                featureSize,
                learningRate,
                l2,
                modelPath,
                persistenceEnabled,
                saveEvery
        );

        System.out.println("✅ OnlineStrategy enabled | lookback=" + lookback +
                ", featureSize=" + featureSize +
                ", threshold=" + threshold +
                ", buyMargin=" + buyMargin +
                ", featureScale=" + featureScale +
                ", rewardK=" + rewardK +
                ", minEdge=" + minEdge +
                ", minVol=" + minVol +
                ", minRetAbs=" + minRetAbs +
                ", minMomentumToVol=" + minMomentumToVol +
                ", minBuyEdgeAbs=" + minBuyEdgeAbs +
                ", exploration=" + explorationEnabled +
                ", forceBuyEvery=" + forceBuyEvery +
                ", isLiveMode=" + isLiveMode +
                ", cfgMode=" + mode +
                ", modelPath=" + modelPath +
                ", persistenceEnabled=" + persistenceEnabled);
    }

    public void setLiveMode(boolean liveMode) {
        this.isLiveMode = liveMode;
    }

    public void setInPosition(boolean inPosition) {
        this.inPosition = inPosition;
        if (inPosition) flatHoldCounterExploration = 0;
    }

    public double getThreshold() { return threshold; }
    public double getRewardK() { return rewardK; }
    public OnlineModel getModel() { return model; }
    public int getLookback() { return lookback; }
    public double getFeatureScale() { return featureScale; }
    public String getModelPath() { return modelPath; }

    // ✅ reset_model: delete the file, reset weights, and IMMEDIATELY create a new online_model.csv
    public synchronized String resetModel() {
        StringBuilder sb = new StringBuilder();
        sb.append("🧽 reset_model executed\n");

        long before = model.getUpdatesCount();
        sb.append("updates before: ").append(before).append("\n");

        boolean deleted = model.deleteModelFile();
        sb.append("File deletion: ").append(deleted ? "✅ deleted" : "⚠ not found/not deleted").append("\n");

        model.reset();
        sb.append("updates after reset(): ").append(model.getUpdatesCount()).append("\n");

        boolean saved = model.saveNow();
        sb.append("File creation (saveNow): ").append(saved ? "✅ created" : "⚠ not created").append("\n");

        sb.append("Model file: ").append(modelPath);

        return sb.toString();
    }

    public double[] extractFeatures(List<Candle> history) {
        if (history == null || history.size() < 2) return null;
        return buildFeatures(history, history.size());
    }

    @Override
    public String getParamsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 ONLINE strategy\n\n");
        sb.append("⚙ Parameters:\n");
        sb.append("  • lookback: ").append(lookback).append("\n");
        sb.append("  • threshold: ").append(threshold).append("\n");
        sb.append("  • buyMargin: ").append(buyMargin).append("\n");
        sb.append("  • rewardK: ").append(rewardK).append("\n");
        sb.append("  • featureScale: ").append(featureScale).append("\n");
        sb.append("  • minEdge: ").append(minEdge).append("\n");
        sb.append("  • minVol: ").append(minVol).append("\n");
        sb.append("  • minRetAbs: ").append(minRetAbs).append("\n");
        sb.append("  • minMomentumToVol: ").append(minMomentumToVol).append("\n");
        sb.append("  • minBuyEdgeAbs: ").append(minBuyEdgeAbs).append("\n");
        sb.append("  • explorationEnabled: ").append(explorationEnabled).append("\n");
        sb.append("  • forceBuyEvery: ").append(forceBuyEvery).append("\n");
        sb.append("  • isLiveMode: ").append(isLiveMode).append("\n");
        sb.append("  • modelPath: ").append(modelPath).append("\n\n");
        sb.append(model.getPrettyStatus());
        return sb.toString();
    }

    @Override
    public TradeAction decide(List<Candle> history) {

        if (history == null || history.size() < lookback + 1) return TradeAction.HOLD;

        double[] features = extractFeatures(history);
        if (features == null) return TradeAction.HOLD;

        double p = model.predictProba(features);

        double upper = 0.5 + threshold;
        double lower = 0.5 - threshold;
        double edge = Math.abs(p - 0.5);

        double volScaled = features[lookback + 1];
        double volRaw = (featureScale != 0.0) ? (volScaled / featureScale) : volScaled;

        double momentumScaled = features[lookback + 2];
        double momentumRaw = (featureScale != 0.0) ? (momentumScaled / featureScale) : momentumScaled;

        int n = history.size();
        double cPrev = history.get(n - 2).close();
        double cLast = history.get(n - 1).close();
        double lastRet = (cPrev > 0.0) ? (cLast - cPrev) / cPrev : 0.0;

        boolean filteredHold = false;

        if (edge < minEdge) filteredHold = true;
        if (volRaw < minVol) filteredHold = true;
        if (Math.abs(lastRet) < minRetAbs) filteredHold = true;
        if (volRaw > 0.0 && Math.abs(momentumRaw) < volRaw * minMomentumToVol) filteredHold = true;

        if (debugProba) {
            System.out.println("[OnlineStrategy] p=" + p +
                    " upper=" + upper + " lower=" + lower +
                    " edge=" + edge +
                    " volRaw=" + volRaw +
                    " momentumRaw=" + momentumRaw +
                    " lastRet=" + lastRet +
                    " filteredHold=" + filteredHold +
                    " inPosition=" + inPosition +
                    " isLiveMode=" + isLiveMode +
                    " updates=" + model.getUpdatesCount() +
                    " explCnt=" + flatHoldCounterExploration);
        }

        if (inPosition) {
            flatHoldCounterExploration = 0;
            if (!filteredHold && p <= lower) return TradeAction.SELL;
            return TradeAction.HOLD;
        }

        if (filteredHold) {
            return maybeExploreForceBuy();
        }

        double minBuyEdge = Math.max(minBuyEdgeAbs, minEdge * 4.0);

        if (p >= (upper + buyMargin) && edge >= minBuyEdge) {
            flatHoldCounterExploration = 0;
            return TradeAction.BUY;
        }

        return maybeExploreForceBuy();
    }

    private TradeAction maybeExploreForceBuy() {
        if (!isLiveMode && explorationEnabled && forceBuyEvery > 0) {
            flatHoldCounterExploration++;
            if (flatHoldCounterExploration >= forceBuyEvery) {
                flatHoldCounterExploration = 0;
                if (debugProba) System.out.println("🧪 EXPLORATION: force BUY after flat HOLD");
                return TradeAction.BUY;
            }
        } else {
            flatHoldCounterExploration = 0;
        }
        return TradeAction.HOLD;
    }

    private double[] buildFeatures(List<Candle> history, int endExclusive) {

        int n = Math.min(endExclusive, history.size());
        if (n < 2) return null;

        double[] features = new double[lookback + 3];
        List<Double> ret = new ArrayList<>();

        int start = Math.max(0, n - lookback - 1);

        for (int i = start + 1; i < n; i++) {
            double c1 = history.get(i - 1).close();
            double c2 = history.get(i).close();
            if (c1 > 0) ret.add(Math.log(c2 / c1));
            else ret.add(0.0);
        }

        int offset = lookback - ret.size();
        for (int i = 0; i < lookback; i++) {
            features[i] = (i < offset) ? 0.0 : ret.get(i - offset);
        }

        double sum = 0.0;
        for (double r : ret) sum += r;
        double mean = ret.isEmpty() ? 0.0 : sum / ret.size();

        double var = 0.0;
        for (double r : ret) var += (r - mean) * (r - mean);
        double vol = ret.size() > 1 ? Math.sqrt(var / (ret.size() - 1)) : 0.0;

        double momentum = sum;

        features[lookback] = mean;
        features[lookback + 1] = vol;
        features[lookback + 2] = momentum;

        if (featureScale != 1.0) {
            for (int i = 0; i < features.length; i++) features[i] *= featureScale;
        }

        return features;
    }
}