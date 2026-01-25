package com.quantor.domain.ai;

import java.io.*;
import java.util.*;

/**
 * Stores trade history and aggregated statistics.
 *
 * File format:
 *   #peak=1234.56
 *   timestamp;pnl;equityAfter
 */
public class AiStatsTracker {

    public static class TradeRecord {
        public final long timestamp;
        public final double pnl;
        public final double equityAfter;

        public TradeRecord(long ts, double pnl, double equity) {
            this.timestamp = ts;
            this.pnl = pnl;
            this.equityAfter = equity;
        }
    }

    private final List<TradeRecord> trades = new ArrayList<>();
    private final File saveFile;

    // Peak equity persists across restarts
    private double peakEquity = 1000.0;

    public AiStatsTracker(String filePath) {
        this.saveFile = new File(filePath);
        load();
    }

    /** Add a closed trade */
    public synchronized void onTradeClosed(double pnl, double equity) {
        updatePeak(equity);
        trades.add(new TradeRecord(System.currentTimeMillis(), pnl, equity));
        save();
    }

    public synchronized List<TradeRecord> getTrades() {
        return new ArrayList<>(trades);
    }

    public synchronized void reset() {
        trades.clear();
        peakEquity = 1000.0;
        save();
    }

    public synchronized int getTotalTrades() {
        return trades.size();
    }

    public synchronized double getWinrate() {
        if (trades.isEmpty()) return 0.0;
        long wins = trades.stream().filter(t -> t.pnl > 0).count();
        return wins * 1.0 / trades.size();
    }

    public synchronized double getAvgPnl() {
        if (trades.isEmpty()) return 0.0;
        return trades.stream().mapToDouble(t -> t.pnl).average().orElse(0);
    }

    /** Maximum drawdown in USDT (absolute) */
    public synchronized double getMaxDrawdown() {
        if (trades.isEmpty()) return 0;
        double peak = trades.get(0).equityAfter;
        double maxDD = 0;
        for (TradeRecord t : trades) {
            if (t.equityAfter > peak) peak = t.equityAfter;
            double dd = peak - t.equityAfter;
            if (dd > maxDD) maxDD = dd;
        }
        return maxDD;
    }

    public synchronized int getWinStreak() {
        int streak = 0;
        for (int i = trades.size() - 1; i >= 0; i--) {
            if (trades.get(i).pnl > 0) streak++;
            else break;
        }
        return streak;
    }

    public synchronized int getLossStreak() {
        int streak = 0;
        for (int i = trades.size() - 1; i >= 0; i--) {
            if (trades.get(i).pnl < 0) streak++;
            else break;
        }
        return streak;
    }

    public synchronized double getPeakEquity() {
        return peakEquity;
    }

    public synchronized double updatePeak(double equity) {
        if (equity > peakEquity) {
            peakEquity = equity;
            save();
        }
        return peakEquity;
    }

    /** ✅ Drawdown based on current equity (0..1) */
    public synchronized double getDrawdown(double currentEquity) {
        if (peakEquity <= 0) return 0.0;
        return (peakEquity - currentEquity) / peakEquity;
    }

    public synchronized String buildStatsForPrompt() {
        return "Trades=" + getTotalTrades() +
                ", winrate=" + getWinrate() +
                ", avgPnL=" + getAvgPnl() +
                ", maxDrawdown=" + getMaxDrawdown() +
                ", winStreak=" + getWinStreak() +
                ", lossStreak=" + getLossStreak();
    }

    private synchronized void save() {
        try {
            File parent = saveFile.getParentFile();
            if (parent != null) parent.mkdirs();

            try (PrintWriter pw = new PrintWriter(new FileWriter(saveFile))) {
                pw.println("#peak=" + peakEquity);
                for (TradeRecord t : trades) {
                    pw.println(t.timestamp + ";" + t.pnl + ";" + t.equityAfter);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void load() {
        if (!saveFile.exists()) return;

        trades.clear();

        try (BufferedReader br = new BufferedReader(new FileReader(saveFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("#peak=")) {
                    try {
                        peakEquity = Double.parseDouble(
                                line.substring("#peak=".length()).trim()
                        );
                    } catch (Exception ignored) {
                    }
                    continue;
                }

                String[] p = line.split(";");
                if (p.length == 3) {
                    long ts = Long.parseLong(p[0]);
                    double pnl = Double.parseDouble(p[1]);
                    double eq = Double.parseDouble(p[2]);
                    trades.add(new TradeRecord(ts, pnl, eq));
                    if (eq > peakEquity) peakEquity = eq;
                }
            }
        } catch (Exception ignored) {
        }
    }
}