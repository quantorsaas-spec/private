package com.quantor.application.ports;

/**
 * Application port for persisting executed trades (journal/audit).
 * Implementations live in infrastructure (SQLite, Postgres, CSV, etc.).
 */
public interface TradeJournalPort {
    void logTrade(String mode,
                  String symbol,
                  String side,
                  double price,
                  double qty,
                  double balanceAfter,
                  String comment);
}
