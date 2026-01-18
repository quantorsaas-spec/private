package com.quantor.domain.stats;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * A unified trade journal for backtest/paper/live.
 * Writes CSV and can compute basic metrics.
 */
public class TradeJournal {

    public static class Record {
        public final long ts;
        public final String mode;      // backtest/paper/live
        public final String symbol;
        public final String interval;
        public final String side;      // BUY/SELL/SL/TP/EXIT_SLTP
        public final double entryPrice;
        public final double exitPrice;
        public final double qty;
        public final double feeOpen;
        public final double feeClose;
        public final double pnl;
        public final double retClose;
        public final double ret3;
        public final double ret5;
        public final String note;

        public Record(long ts, String mode, String symbol, String interval, String side,
                      double entryPrice, double exitPrice, double qty,
                      double feeOpen, double feeClose, double pnl,
                      double retClose, double ret3, double ret5, String note) {
            this.ts = ts;
            this.mode = mode;
            this.symbol = symbol;
            this.interval = interval;
            this.side = side;
            this.entryPrice = entryPrice;
            this.exitPrice = exitPrice;
            this.qty = qty;
            this.feeOpen = feeOpen;
            this.feeClose = feeClose;
            this.pnl = pnl;
            this.retClose = retClose;
            this.ret3 = ret3;
            this.ret5 = ret5;
            this.note = note;
        }
    }

    private final Path csvPath;

    public TradeJournal(String csvFile) {
        this.csvPath = Path.of(csvFile);
        try {
            if (csvPath.getParent() != null) Files.createDirectories(csvPath.getParent());
            if (!Files.exists(csvPath)) {
                Files.writeString(csvPath, header() + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
        } catch (Exception e) {
            System.err.println("[TradeJournal] init error: " + e.getMessage());
        }
    }

    private String header() {
        return "ts,mode,symbol,interval,side,entryPrice,exitPrice,qty,feeOpen,feeClose,pnl,retClose,ret3,ret5,note";
    }

    public synchronized void append(Record r) {
        String line = String.join(",",
                String.valueOf(r.ts),
                esc(r.mode),
                esc(r.symbol),
                esc(r.interval),
                esc(r.side),
                fmt(r.entryPrice),
                fmt(r.exitPrice),
                fmt(r.qty),
                fmt(r.feeOpen),
                fmt(r.feeClose),
                fmt(r.pnl),
                fmt(r.retClose),
                fmt(r.ret3),
                fmt(r.ret5),
                esc(r.note)
        );

        try {
            Files.writeString(csvPath, line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("[TradeJournal] append error: " + e.getMessage());
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        String x = s.replace("\"", "\"\"");
        if (x.contains(",") || x.contains("\n")) return "\"" + x + "\"";
        return x;
    }

    private String fmt(double v) {
        return String.format(Locale.US, "%.10f", v);
    }

    public synchronized List<Record> readAll() {
        if (!Files.exists(csvPath)) return List.of();
        List<Record> out = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                // simple CSV parsing (no complex quoted numerics)
                String[] p = splitCsvLine(line);
                if (p.length < 15) continue;

                long ts = Long.parseLong(p[0]);
                String mode = p[1];
                String symbol = p[2];
                String interval = p[3];
                String side = p[4];
                double entryPrice = Double.parseDouble(p[5]);
                double exitPrice  = Double.parseDouble(p[6]);
                double qty        = Double.parseDouble(p[7]);
                double feeOpen    = Double.parseDouble(p[8]);
                double feeClose   = Double.parseDouble(p[9]);
                double pnl        = Double.parseDouble(p[10]);
                double retClose   = Double.parseDouble(p[11]);
                double ret3       = Double.parseDouble(p[12]);
                double ret5       = Double.parseDouble(p[13]);
                String note       = p[14];

                out.add(new Record(ts, mode, symbol, interval, side,
                        entryPrice, exitPrice, qty, feeOpen, feeClose, pnl,
                        retClose, ret3, ret5, note));
            }
        } catch (Exception e) {
            System.err.println("[TradeJournal] readAll error: " + e.getMessage());
        }
        return out;
    }

    // Minimal CSV split with quotes
    private String[] splitCsvLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQ = !inQ;
                continue;
            }
            if (c == ',' && !inQ) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }

    // ---------------- METRICS ----------------

    public static class Summary {
        public final int trades;
        public final double pnlTotal;
        public final double winrate;
        public final double avgPnl;
        public final double profitFactor;
        public final double maxDrawdown; // cumulative pnl-based

        public Summary(int trades, double pnlTotal, double winrate, double avgPnl, double profitFactor, double maxDrawdown) {
            this.trades = trades;
            this.pnlTotal = pnlTotal;
            this.winrate = winrate;
            this.avgPnl = avgPnl;
            this.profitFactor = profitFactor;
            this.maxDrawdown = maxDrawdown;
        }
    }

    public Summary summarize(List<Record> records) {
        if (records == null || records.isEmpty()) return new Summary(0, 0, 0, 0, 0, 0);

        int n = records.size();
        double total = 0, pos = 0, neg = 0;
        int wins = 0;

        double equity = 0;
        double peak = 0;
        double maxDD = 0;

        for (Record r : records) {
            total += r.pnl;
            if (r.pnl > 0) { pos += r.pnl; wins++; }
            if (r.pnl < 0) { neg += -r.pnl; }

            equity += r.pnl;
            if (equity > peak) peak = equity;
            double dd = peak - equity;
            if (dd > maxDD) maxDD = dd;
        }

        double winrate = wins * 1.0 / n;
        double avg = total / n;
        double pf = (neg == 0) ? (pos > 0 ? 999.0 : 0.0) : (pos / neg);

        return new Summary(n, total, winrate, avg, pf, maxDD);
    }

    public String pretty(Summary s) {
        return "📊 TradeJournal\n" +
                "Trades: " + s.trades + "\n" +
                "PnL total: " + String.format(Locale.US, "%.4f", s.pnlTotal) + "\n" +
                "Winrate: " + String.format(Locale.US, "%.2f%%", s.winrate * 100) + "\n" +
                "Avg PnL: " + String.format(Locale.US, "%.4f", s.avgPnl) + "\n" +
                "Profit Factor: " + String.format(Locale.US, "%.4f", s.profitFactor) + "\n" +
                "Max DD (cum pnl): " + String.format(Locale.US, "%.4f", s.maxDrawdown);
    }
}