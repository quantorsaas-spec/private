package com.quantor.application.ports;

/** Safe default when no journal is wired. */
public final class NoopTradeJournal implements TradeJournalPort {
    @Override
    public void logTrade(String mode, String symbol, String side, double price, double qty, double balanceAfter, String comment) {
        // no-op
    }
}
