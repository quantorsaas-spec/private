package com.quantor.domain.strategy.online;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Locale;

/**
 * A minimal online model (logistic regression).
 * Stores weights w and bias b, supports predict/update and (optionally) persistence to a file.
 */
public class OnlineModel {
    private volatile boolean debugUpdateTrace = false;

    public void setDebugUpdateTrace(boolean enabled) {
        this.debugUpdateTrace = enabled;
    }

    private final int featureSize;
    private final double learningRate;
    private final double l2;
    private final String modelPath;
    private final boolean persistenceEnabled;
    private final int saveEvery;

    private double[] w;
    private double b;

    private long updatesCount = 0;

    public OnlineModel(int featureSize,
                       double learningRate,
                       double l2,
                       String modelPath,
                       boolean persistenceEnabled,
                       int saveEvery) {

        this.featureSize = featureSize;
        this.learningRate = learningRate;
        this.l2 = l2;
        this.modelPath = modelPath;
        this.persistenceEnabled = persistenceEnabled;
        this.saveEvery = saveEvery;

        this.w = new double[featureSize];
        this.b = 0.0;

        if (persistenceEnabled) {
            tryLoad();
        }
    }

    public long getUpdatesCount() {
        return updatesCount;
    }

    public double predictProba(double[] x) {
        if (x == null || x.length != featureSize) return 0.5;
        double z = b;
        for (int i = 0; i < featureSize; i++) z += w[i] * x[i];
        return sigmoid(z);
    }

    public void update(double[] x, double y01) {
        if (x == null || x.length != featureSize) return;

        double p = predictProba(x);
        double err = (p - y01);

        for (int i = 0; i < featureSize; i++) {
            double grad = err * x[i] + l2 * w[i];
            w[i] -= learningRate * grad;
        }
        b -= learningRate * err;

        if (debugUpdateTrace) {
            System.out.println("[OnlineModel] UPDATE called in thread=" + Thread.currentThread().getName());
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            for (int i = 2; i < Math.min(st.length, 7); i++) {
                System.out.println("  at " + st[i]);
            }
        }

        updatesCount++;

        // ✅ FIX: if the model file was deleted — recreate it on the first update
        if (persistenceEnabled) {
            boolean fileMissing = false;
            try {
                if (modelPath != null && !modelPath.isBlank()) {
                    fileMissing = !Files.exists(Paths.get(modelPath));
                }
            } catch (Exception ignored) {}

            if (fileMissing || (saveEvery > 0 && updatesCount % saveEvery == 0)) {
                trySave();
            }
        }
    }

    public String getPrettyStatus() {
        return "🧠 OnlineModel\n" +
                "  • featureSize: " + featureSize + "\n" +
                "  • learningRate: " + learningRate + "\n" +
                "  • l2: " + l2 + "\n" +
                "  • persistenceEnabled: " + persistenceEnabled + "\n" +
                "  • modelPath: " + modelPath + "\n" +
                "  • updatesCount: " + updatesCount + "\n";
    }

    /** Force-save the model to disk right now. */
    public synchronized boolean saveNow() {
        if (!persistenceEnabled) return false;
        if (modelPath == null || modelPath.isBlank()) return false;
        trySave();
        try {
            return Files.exists(Paths.get(modelPath));
        } catch (Exception e) {
            return false;
        }
    }

    /** Reset the model: weights=0, bias=0, updates=0. */
    public synchronized void reset() {
        Arrays.fill(this.w, 0.0);
        this.b = 0.0;
        this.updatesCount = 0;
    }

    /** Deletes the model file on disk (if it exists). */
    public synchronized boolean deleteModelFile() {
        try {
            if (modelPath == null || modelPath.isBlank()) return false;
            Path p = Paths.get(modelPath);
            return Files.deleteIfExists(p);
        } catch (Exception e) {
            return false;
        }
    }

    private void tryLoad() {
        try {
            if (modelPath == null || modelPath.isBlank()) return;
            Path p = Paths.get(modelPath);
            if (!Files.exists(p)) return;

            try (BufferedReader br = Files.newBufferedReader(p)) {
                String line = br.readLine();
                if (line == null) return;

                String[] parts = line.split(",");
                if (parts.length < featureSize + 2) return;

                for (int i = 0; i < featureSize; i++) {
                    w[i] = Double.parseDouble(parts[i]);
                }
                b = Double.parseDouble(parts[featureSize]);
                updatesCount = Long.parseLong(parts[featureSize + 1]);
            }
        } catch (Exception ignored) {}
    }

    private void trySave() {
        try {
            if (modelPath == null || modelPath.isBlank()) return;

            Path p = Paths.get(modelPath);
            Path parent = p.getParent();
            if (parent != null) Files.createDirectories(parent);

            try (BufferedWriter bw = Files.newBufferedWriter(p,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < featureSize; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(String.format(Locale.US, "%.15f", w[i]));
                }
                sb.append(",").append(String.format(Locale.US, "%.15f", b));
                sb.append(",").append(updatesCount);

                bw.write(sb.toString());
            }
        } catch (IOException ignored) {}
    }

    private static double sigmoid(double z) {
        if (z >= 0) {
            double t = Math.exp(-z);
            return 1.0 / (1.0 + t);
        } else {
            double t = Math.exp(z);
            return t / (1.0 + t);
        }
    }
}