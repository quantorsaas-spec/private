package com.quantor.infrastructure.journal;

import com.quantor.application.ports.TradeJournalPort;
import com.quantor.infrastructure.db.TradeLogger;

/**
 * Simple TradeJournalPort implementation backed by the existing SQLite TradeLogger.
 *
 * Notes:
 * - Keeps the domain/application layers free from DB dependencies.
 * - Safe to use for MVP and investor demos.
 */
public class SqliteTradeJournalAdapter implements TradeJournalPort {

    @Override
    public void logTrade(String mode, String symbol, String side, double price, double qty, double balanceAfter, String comment) {
        TradeLogger.log(mode, symbol, side, price, qty, balanceAfter, comment);
    }
}
