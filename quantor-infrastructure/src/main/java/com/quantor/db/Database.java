package com.quantor.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public final class Database {

    private Database() {}

    public static final String DB_URL = "jdbc:sqlite:data/binancebot.db";

    public static void ensureDataDir() {
        try {
            Files.createDirectories(Path.of("data"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create data/ directory", e);
        }
    }

    public static Connection getConnection() {
        try {
            ensureDataDir();
            Connection c = DriverManager.getConnection(DB_URL);

            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");
                st.execute("PRAGMA foreign_keys=ON;");
            }

            return c;
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to SQLite: " + DB_URL, e);
        }
    }

    /** Schema initialization (strategies + trades + indexes). */
    public static void initSchema() {
        ensureDataDir();

        String sql = """
        CREATE TABLE IF NOT EXISTS trades (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          ts TEXT NOT NULL,
          mode TEXT,
          symbol TEXT,
          side TEXT,
          price REAL,
          qty REAL,
          balance_after REAL,
          comment TEXT
        );

        CREATE TABLE IF NOT EXISTS strategies (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          name TEXT NOT NULL UNIQUE,
          enabled INTEGER NOT NULL DEFAULT 1,
          params_json TEXT NOT NULL,
          created_at TEXT NOT NULL,
          updated_at TEXT NOT NULL
        );

        

        CREATE TABLE IF NOT EXISTS trading_sessions (
          id TEXT PRIMARY KEY,
          user_id TEXT NOT NULL,
          exchange_id TEXT NOT NULL,
          account_id TEXT NOT NULL,
          strategy_id TEXT NOT NULL,
          status TEXT NOT NULL,
          started_at TEXT,
          stopped_at TEXT,
          stop_code TEXT,
          stop_message TEXT,
          pnl_realized REAL NOT NULL DEFAULT 0,
          pnl_unrealized REAL NOT NULL DEFAULT 0,
          pnl_updated_at TEXT,
          created_at TEXT NOT NULL,
          updated_at TEXT NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_sessions_user_status ON trading_sessions(user_id, status);

        CREATE TABLE IF NOT EXISTS user_subscriptions (
          user_id TEXT PRIMARY KEY,
          subscription_id TEXT NOT NULL,
          variant_id INTEGER,
          status TEXT NOT NULL,
          renews_at TEXT,
          ends_at TEXT,
          updated_at TEXT NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_user_subscriptions_status ON user_subscriptions(status);

CREATE INDEX IF NOT EXISTS idx_trades_mode ON trades(mode);
        CREATE INDEX IF NOT EXISTS idx_trades_symbol ON trades(symbol);
        """;

        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        } catch (Exception e) {
            throw new RuntimeException("initSchema() error", e);
        }
    }
}