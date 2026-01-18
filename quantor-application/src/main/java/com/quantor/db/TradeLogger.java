package com.quantor.db;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal static trade logger.
 * Writes to console and (best-effort) appends CSV into ./reports/trades.csv
 */
public final class TradeLogger {

    private TradeLogger() {}

    public static void log(String mode,
                           String symbol,
                           String action,
                           double price,
                           double qty,
                           double cash,
                           String note) {

        String safeNote = (note == null) ? "" : note;
        String line = String.format("[%s] %s %s price=%.8f qty=%.8f cash=%.4f | %s",
                mode, symbol, action, price, qty, cash, safeNote);
        System.out.println(line);

        // Best-effort CSV
        try {
            Path dir = Path.of("reports");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path file = dir.resolve("trades.csv");
            boolean exists = Files.exists(file);

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file.toFile(), true))) {
                if (!exists) {
                    bw.write("mode,symbol,action,price,qty,cash,note");
                    bw.newLine();
                }
                bw.write(csv(mode) + "," + csv(symbol) + "," + csv(action) + ","
                        + price + "," + qty + "," + cash + "," + csv(safeNote));
                bw.newLine();
            }
        } catch (Exception ignore) {
            // ignore
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        String x = s.replace("\"", "\"\"");
        boolean needQuotes = x.contains(",") || x.contains("\n") || x.contains("\r");
        return needQuotes ? ("\"" + x + "\"") : x;
    }
}
