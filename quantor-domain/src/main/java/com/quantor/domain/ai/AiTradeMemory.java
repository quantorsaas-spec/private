package com.quantor.domain.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Trade memory for the AI coach.
 * Stores the last N closed trades.
 */
public class AiTradeMemory {

    /**
     * Description of a single closed trade.
     */
    public static class TradeRecord {
        private final Instant time;
        private final String symbol;
        private final String side;        // e.g. "LONG"
        private final double entryPrice;  // entry price
        private final double exitPrice;   // exit price
        private final double pnl;         // trade PnL
        private final double equityAfter; // equity after the trade
        private final String reason;      // close reason (SL / TP / SELL signal, etc.)

        public TradeRecord(Instant time,
                           String symbol,
                           String side,
                           double entryPrice,
                           double exitPrice,
                           double pnl,
                           double equityAfter,
                           String reason) {
            this.time = time;
            this.symbol = symbol;
            this.side = side;
            this.entryPrice = entryPrice;
            this.exitPrice = exitPrice;
            this.pnl = pnl;
            this.equityAfter = equityAfter;
            this.reason = reason;
        }

        public Instant getTime() {
            return time;
        }

        public String getSymbol() {
            return symbol;
        }

        public String getSide() {
            return side;
        }

        public double getEntryPrice() {
            return entryPrice;
        }

        public double getExitPrice() {
            return exitPrice;
        }

        public double getPnl() {
            return pnl;
        }

        public double getEquityAfter() {
            return equityAfter;
        }

        public String getReason() {
            return reason;
        }
    }

    private final int capacity;
    private final LinkedList<TradeRecord> trades = new LinkedList<>();

    /**
     * @param capacity number of most recent trades to keep
     */
    public AiTradeMemory(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Record a newly closed trade into memory.
     */
    public synchronized void recordTrade(String symbol,
                                         String side,
                                         double entryPrice,
                                         double exitPrice,
                                         double pnl,
                                         double equityAfter,
                                         String reason) {
        TradeRecord rec = new TradeRecord(
                Instant.now(),
                symbol,
                side,
                entryPrice,
                exitPrice,
                pnl,
                equityAfter,
                reason
        );

        trades.addLast(rec);
        // Enforce buffer size limit
        while (trades.size() > capacity) {
            trades.removeFirst();
        }
    }

    /**
     * Returns the last N trades, ordered from most recent to older.
     */
    public synchronized List<TradeRecord> getRecentTrades(int limit) {
        int n = Math.min(limit, trades.size());
        List<TradeRecord> res = new ArrayList<>(n);

        int count = 0;
        var it = trades.descendingIterator(); // iterate from newest to oldest
        while (it.hasNext() && count < n) {
            res.add(it.next());
            count++;
        }
        return res;
    }

    /**
     * Returns all trades (copy of the list).
     */
    public synchronized List<TradeRecord> getAllTrades() {
        return new ArrayList<>(trades);
    }

    public synchronized int size() {
        return trades.size();
    }
}