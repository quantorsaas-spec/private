package com.quantor.infrastructure.db;

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

        CREATE INDEX IF NOT EXISTS idx_trades_mode ON trades(mode);
        CREATE INDEX IF NOT EXISTS idx_trades_symbol ON trades(symbol);

        CREATE TABLE IF NOT EXISTS user_secrets (
          user_id TEXT NOT NULL,
          secret_key TEXT NOT NULL,
          secret_value_enc TEXT NOT NULL,
          updated_at TEXT NOT NULL,
          PRIMARY KEY (user_id, secret_key)
        );

        CREATE INDEX IF NOT EXISTS idx_user_secrets_user ON user_secrets(user_id);
        """;

        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        } catch (Exception e) {
            throw new RuntimeException("initSchema() error", e);
        }
    }
}