package com.quantor.domain.strategy.online;



import com.quantor.domain.market.Candle;
import com.quantor.domain.order.TradeAction;
import com.quantor.domain.strategy.Strategy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/**
 * Online-learning strategy:
 *
 * - builds features from the most recent returns
 * - maintains weights w[i] and a bias term
 * - on each step:
 *      1) updates weights using the previous observation (SGD)
 *      2) computes prediction yHat = w·x + bias
 *      3) yHat > threshold   → BUY
 *         yHat < -threshold  → SELL
 *         otherwise          → HOLD
 *
 * - can persist the model to a file (modelPath)
 * - can load it on startup
 * - provides resetModel() to "forget" training
 *
 * This is an educational example of online learning,
 * not a guaranteed profitable strategy.
 */
public class OnlineLearningStrategy implements Strategy {

    /** How many recent returns to use as features */
    private final int lookback;

    /** Learning rate (SGD) */
    private final double learningRate;

    /** Threshold for BUY/SELL signals based on predicted return */
    private final double threshold;

    /** Path to the model file */
    private final String modelPath;

    /** Whether persistence (load/save) is enabled */
    private final boolean persistenceEnabled;

    /** How often to save the model (every N calls to decide) */
    private final int saveEverySteps;

    /** Linear model weights */
    private final double[] w;

    /** Bias term */
    private double bias = 0.0;

    /** Previous observation (features) */
    private double[] prevX;

    /** Previous actual return */
    private double prevY;

    /** Whether we have a previous observation to update from */
    private boolean hasPrev = false;

    /** Step counter for periodic persistence */
    private int stepCounter = 0;

    public OnlineLearningStrategy(
            int lookback,
            double learningRate,
            double threshold,
            String modelPath,
            boolean persistenceEnabled,
            int saveEverySteps
    ) {
        if (lookback <= 0) {
            throw new IllegalArgumentException("lookback must be > 0");
        }
        if (saveEverySteps <= 0) {
            throw new IllegalArgumentException("saveEverySteps must be > 0");
        }

        this.lookback = lookback;
        this.learningRate = learningRate;
        this.threshold = threshold;
        this.modelPath = modelPath;
        this.persistenceEnabled = persistenceEnabled;
        this.saveEverySteps = saveEverySteps;

        this.w = new double[lookback]; // initialized to zeros

        if (persistenceEnabled) {
            loadModel();
        }
    }

    @Override
    public synchronized TradeAction decide(List<Candle> candles) {
        if (candles == null || candles.size() < lookback + 2) {
            return TradeAction.HOLD;
        }

        int n = candles.size();

        // 1️⃣ UPDATE WEIGHTS using the previous observation
        if (hasPrev && prevX != null) {
            double yHatPrev = dot(w, prevX) + bias;
            double error = prevY - yHatPrev;

            for (int i = 0; i < w.length; i++) {
                w[i] += learningRate * error * prevX[i];
            }
            bias += learningRate * error;
        }

        // 2️⃣ BUILD A NEW FEATURE VECTOR x from returns
        double[] x = new double[lookback];

        for (int i = 0; i < lookback; i++) {
            double c1 = candles.get(n - 1 - i).close();
            double c0 = candles.get(n - 2 - i).close();
            double ret = (c1 - c0) / c0;
            x[i] = ret;
        }

        // 3️⃣ ACTUAL return between the last two candles
        double lastClose = candles.get(n - 1).close();
        double prevClose = candles.get(n - 2).close();
        double currentReturn = (lastClose - prevClose) / prevClose;

        this.prevX = x;
        this.prevY = currentReturn;
        this.hasPrev = true;

        // 4️⃣ PREDICTION
        double yHat = dot(w, x) + bias;

        // 5️⃣ PERIODIC MODEL SAVE
        stepCounter++;
        if (persistenceEnabled && stepCounter % saveEverySteps == 0) {
            saveModel();
        }

        // 6️⃣ DECISION
        if (yHat > threshold) {
            return TradeAction.BUY;
        } else if (yHat < -threshold) {
            return TradeAction.SELL;
        } else {
            return TradeAction.HOLD;
        }
    }

    // ------------------ HELPERS ------------------

    private double dot(double[] a, double[] b) {
        double s = 0.0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            s += a[i] * b[i];
        }
        return s;
    }

    private void loadModel() {
        try {
            if (modelPath == null || modelPath.isBlank()) return;

            Path path = Paths.get(modelPath);
            if (!Files.exists(path)) return;

            String line = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (line.isEmpty()) return;

            String[] parts = line.split(",");
            if (parts.length < w.length + 1) return;

            for (int i = 0; i < w.length; i++) {
                w[i] = Double.parseDouble(parts[i]);
            }
            bias = Double.parseDouble(parts[w.length]);

            System.out.println("[ONLINE AI] Model loaded from " + modelPath);

        } catch (Exception e) {
            System.err.println("[ONLINE AI] Failed to load model: " + e.getMessage());
        }
    }

    private void saveModel() {
        try {
            if (modelPath == null || modelPath.isBlank()) return;

            Path path = Paths.get(modelPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < w.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(w[i]);
            }
            sb.append(',').append(bias);

            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            System.out.println("[ONLINE AI] Model saved to " + modelPath);

        } catch (IOException e) {
            System.err.println("[ONLINE AI] Failed to save model: " + e.getMessage());
        }
    }

    // ------------------ SERVICE METHODS FOR /ai_status /ai_reset ------------------

    public synchronized void resetModel(boolean deleteFile) {
        for (int i = 0; i < w.length; i++) {
            w[i] = 0.0;
        }
        bias = 0.0;
        hasPrev = false;
        prevX = null;
        prevY = 0.0;
        stepCounter = 0;

        if (deleteFile && modelPath != null && !modelPath.isBlank()) {
            try {
                Path path = Paths.get(modelPath);
                if (Files.exists(path)) {
                    Files.delete(path);
                }
            } catch (IOException e) {
                System.err.println("[ONLINE AI] Failed to delete model file: " + e.getMessage());
            }
        }

        System.out.println("[ONLINE AI] Model reset (resetModel)");
    }

    public int getLookback() {
        return lookback;
    }

    public double getLearningRate() {
        return learningRate;
    }

    public double getThreshold() {
        return threshold;
    }

    public String getModelPath() {
        return modelPath;
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public int getSaveEverySteps() {
        return saveEverySteps;
    }
}