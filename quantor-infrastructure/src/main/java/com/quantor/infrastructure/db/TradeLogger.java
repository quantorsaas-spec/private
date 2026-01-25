package com.quantor.infrastructure.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

public class TradeLogger {

    static {
        Database.initSchema();
    }

    public static void log(
            String mode,
            String symbol,
            String side,
            double price,
            double qty,
            double balanceAfter,
            String comment
    ) {
        String sql = """
            INSERT INTO trades (ts, mode, symbol, side, price, qty, balance_after, comment)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, new Timestamp(System.currentTimeMillis()).toString());
            ps.setString(2, mode);
            ps.setString(3, symbol);
            ps.setString(4, side);
            ps.setDouble(5, price);
            ps.setDouble(6, qty);
            ps.setDouble(7, balanceAfter);
            ps.setString(8, comment);

            ps.executeUpdate();

        } catch (Exception e) {
            System.err.println("❌ TradeLogger.log() DB error: " + e.getMessage());
        }
    }
}