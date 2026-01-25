package com.quantor.infrastructure.db;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TradeRepository {

    public record TradeRow(long id, String ts, String mode, String symbol, String side,
                           Double price, Double qty, Double balanceAfter, String comment) {}

    public List<TradeRow> list(String mode, String symbol, int limit) throws SQLException {
        if (limit <= 0) limit = 50;

        StringBuilder sql = new StringBuilder("""
            SELECT id,ts,mode,symbol,side,price,qty,balance_after,comment
            FROM trades
            WHERE 1=1
        """);

        List<Object> args = new ArrayList<>();

        if (mode != null && !mode.isBlank()) {
            sql.append(" AND mode = ?");
            args.add(mode.trim());
        }
        if (symbol != null && !symbol.isBlank()) {
            sql.append(" AND symbol = ?");
            args.add(symbol.trim());
        }

        sql.append(" ORDER BY id DESC LIMIT ?");
        args.add(limit);

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {

            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));

            List<TradeRow> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        }
    }

    public int deleteByMode(String mode) throws SQLException {
        String sql = "DELETE FROM trades WHERE mode = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, mode);
            return ps.executeUpdate();
        }
    }

    public int deleteTestTrades() throws SQLException {
        // If needed, the list can be extended
        String sql = "DELETE FROM trades WHERE mode IN ('paper','backtest')";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    public List<TradeRow> listForExport(String mode, String symbol, int limit) throws SQLException {
        if (limit <= 0) limit = 200;

        StringBuilder sql = new StringBuilder("""
        SELECT id,ts,mode,symbol,side,price,qty,balance_after,comment
        FROM trades
        WHERE 1=1
    """);

        List<Object> args = new ArrayList<>();

        if (mode != null && !mode.isBlank()) {
            sql.append(" AND mode = ?");
            args.add(mode.trim());
        }
        if (symbol != null && !symbol.isBlank()) {
            sql.append(" AND symbol = ?");
            args.add(symbol.trim());
        }

        sql.append(" ORDER BY id ASC LIMIT ?");
        args.add(limit);

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {

            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));

            List<TradeRow> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        }
    }

    public Path exportToCsv(String mode, String symbol, Path outPath, int limit) throws Exception {
        Database.ensureDataDir();
        Files.createDirectories(outPath.getParent());

        List<TradeRow> rows = listForExport(mode, symbol, limit);

        try (BufferedWriter w = Files.newBufferedWriter(outPath)) {
            w.write("id,ts,mode,symbol,side,price,qty,balance_after,comment\n");
            for (TradeRow r : rows) {
                w.write(r.id + "," +
                        safe(r.ts) + "," +
                        safe(r.mode) + "," +
                        safe(r.symbol) + "," +
                        safe(r.side) + "," +
                        safeNum(r.price) + "," +
                        safeNum(r.qty) + "," +
                        safeNum(r.balanceAfter) + "," +
                        csv(safe(r.comment)) + "\n");
            }
        }
        return outPath;
    }

    private static TradeRow map(ResultSet rs) throws SQLException {
        return new TradeRow(
                rs.getLong("id"),
                rs.getString("ts"),
                rs.getString("mode"),
                rs.getString("symbol"),
                rs.getString("side"),
                (Double) rs.getObject("price"),
                (Double) rs.getObject("qty"),
                (Double) rs.getObject("balance_after"),
                rs.getString("comment")
        );
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String safeNum(Double d) { return d == null ? "" : String.valueOf(d); }

    private static String csv(String s) {
        // minimal CSV escaping
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }
}