package com.quantor.infrastructure.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TradeReport {

    private record EquityPoint(long id, double balance) {}

    public static String buildSummary(String mode) {

        Database.initSchema();

        String sqlMain =
                "SELECT COUNT(*) AS cnt, MAX(balance_after) AS last_balance " +
                        "FROM trades WHERE mode = ?";

        String sqlSide =
                "SELECT side, COUNT(*) AS cnt " +
                        "FROM trades WHERE mode = ? GROUP BY side";

        String sqlEquity =
                "SELECT id, balance_after FROM trades " +
                        "WHERE mode = ? AND balance_after IS NOT NULL ORDER BY id ASC";

        int total = 0;
        int buys = 0;
        int sells = 0;
        int others = 0;

        List<EquityPoint> equitySeries = new ArrayList<>();

        try (Connection conn = Database.getConnection()) {

            try (PreparedStatement ps = conn.prepareStatement(sqlMain)) {
                ps.setString(1, mode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) total = rs.getInt("cnt");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlSide)) {
                ps.setString(1, mode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String side = rs.getString("side");
                        int c = rs.getInt("cnt");
                        if ("BUY".equalsIgnoreCase(side)) buys = c;
                        else if ("SELL".equalsIgnoreCase(side)) sells = c;
                        else others += c;
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlEquity)) {
                ps.setString(1, mode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        equitySeries.add(new EquityPoint(
                                rs.getLong("id"),
                                rs.getDouble("balance_after")
                        ));
                    }
                }
            }

        } catch (SQLException e) {
            return "⚠ DB report error: " + e.getMessage();
        }

        if (total == 0 || equitySeries.isEmpty()) {
            return "📊 " + mode.toUpperCase() + " | No trades found in the database (mode: " + mode + ").";
        }

        double startBalance = equitySeries.get(0).balance();
        if (startBalance <= 0) startBalance = 1000.0;

        double finalBalance = equitySeries.get(equitySeries.size() - 1).balance();

        double pnlAbs = finalBalance - startBalance;
        double pnlPct = (startBalance != 0.0) ? (pnlAbs / startBalance) * 100.0 : 0.0;

        int wins = 0;
        int losses = 0;

        double maxEquity = equitySeries.get(0).balance();
        double maxDrawdownPct = 0.0;

        for (int i = 1; i < equitySeries.size(); i++) {
            double prev = equitySeries.get(i - 1).balance();
            double curr = equitySeries.get(i).balance();

            double delta = curr - prev;
            if (delta > 0) wins++;
            else if (delta < 0) losses++;

            if (curr > maxEquity) maxEquity = curr;

            if (maxEquity > 0) {
                double dd = (curr - maxEquity) / maxEquity;
                if (dd < maxDrawdownPct) maxDrawdownPct = dd;
            }
        }

        int tradesWithPnl = wins + losses;
        double winrate = tradesWithPnl > 0 ? (wins * 100.0 / tradesWithPnl) : 0.0;
        double maxDrawdownPosPct = -maxDrawdownPct * 100.0;

        String titleEmoji = "paper".equalsIgnoreCase(mode) ? "🧪" : "📡";
        String titleName  = "paper".equalsIgnoreCase(mode) ? "PAPER" : "LIVE";

        StringBuilder sb = new StringBuilder();
        sb.append(titleEmoji).append(" ").append(titleName).append(" | Report for mode=").append(mode).append("\n");
        sb.append("Rows in trades: ").append(total).append("\n");
        sb.append("BUY: ").append(buys).append(", SELL: ").append(sells).append(", other: ").append(others).append("\n\n");

        sb.append("Start balance: ").append(String.format("%.2f", startBalance)).append(" USDT\n");
        sb.append("Final balance: ").append(String.format("%.2f", finalBalance)).append(" USDT\n");

        sb.append("PnL: ").append(String.format("%+.2f", pnlAbs)).append(" USDT (")
                .append(String.format("%+.2f", pnlPct)).append(" %)\n\n");

        sb.append("Trades with PnL: ").append(tradesWithPnl).append("\n");
        sb.append("Wins: ").append(wins).append(", Losses: ").append(losses).append("\n");
        sb.append("Winrate: ").append(String.format("%.2f", winrate)).append(" %\n");
        sb.append("Max drawdown: ").append(String.format("%.2f", maxDrawdownPosPct)).append(" %");

        return sb.toString();
    }
}