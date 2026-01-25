package com.quantor.domain.ai;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

public class TrainLogger {

    private final Path path;

    public TrainLogger(String filePath) {
        this.path = Paths.get(filePath == null || filePath.isBlank()
                ? "data/online_train_log.csv"
                : filePath);
        ensureHeader();
    }

    private void ensureHeader() {
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            if (!Files.exists(path) || Files.size(path) == 0) {
                try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    bw.write("ts_iso;symbol;interval;reason;tick;entry;exit;retClose;ret3;ret5;target;pBefore;pAfter;updates\n");
                }
            }
        } catch (Exception e) {
            System.err.println("[TrainLogger] ensureHeader error: " + e.getMessage());
        }
    }

    public synchronized void log(
            String symbol,
            String interval,
            String reason,
            int tick,
            double entryPrice,
            double exitPrice,
            double retClose,
            Double ret3,
            Double ret5,
            double target,
            double pBefore,
            double pAfter,
            long updates
    ) {
        String ts = Instant.now().toString();

        String line =
                ts + ";" +
                        safe(symbol) + ";" +
                        safe(interval) + ";" +
                        safe(reason) + ";" +
                        tick + ";" +
                        fmt(entryPrice) + ";" +
                        fmt(exitPrice) + ";" +
                        fmt(retClose) + ";" +
                        (ret3 == null ? "" : fmt(ret3)) + ";" +
                        (ret5 == null ? "" : fmt(ret5)) + ";" +
                        fmt(target) + ";" +
                        fmt(pBefore) + ";" +
                        fmt(pAfter) + ";" +
                        updates + "\n";

        try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            bw.write(line);
        } catch (IOException e) {
            System.err.println("[TrainLogger] write error: " + e.getMessage());
        }
    }

    private static String safe(String s) {
        return (s == null) ? "" : s.replace(";", "_");
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.8f", v);
    }
}