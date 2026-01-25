package com.quantor.infrastructure.exchange;

import com.quantor.application.exchange.MarketSymbol;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe in-memory portfolio for PAPER trading.
 *
 * <p>Balances are stored per asset symbol (e.g. BTC, USDT).
 * <p>Fees are charged in quote currency.
 */
public final class PaperPortfolio {

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Double> free = new HashMap<>();

    private PaperPortfolio() {}

    public static PaperPortfolio withInitialBalance(String asset, double amount) {
        PaperPortfolio p = new PaperPortfolio();
        p.free.put(norm(asset), amount);
        return p;
    }

    /** Apply a fill: updates base/quote balances. */
    public void apply(PaperExchangeAdapter.PaperFill fill) {
        Objects.requireNonNull(fill, "fill");
        lock.lock();
        try {
            MarketSymbol s = fill.symbol();
            String base = norm(s.base());
            String quote = norm(s.quote());

            if (fill.side() == PaperExchangeAdapter.PaperFill.Side.BUY) {
                double cost = fill.notional() + fill.fee();
                ensure(quote, cost);
                sub(quote, cost);
                add(base, fill.quantity());
            } else {
                ensure(base, fill.quantity());
                sub(base, fill.quantity());
                double proceeds = fill.notional() - fill.fee();
                add(quote, proceeds);
            }
        } finally {
            lock.unlock();
        }
    }

    /** Snapshot balances (free). */
    public Map<String, Double> balances() {
        lock.lock();
        try {
            return Collections.unmodifiableMap(new HashMap<>(free));
        } finally {
            lock.unlock();
        }
    }

    public PaperPortfolio copy() {
        lock.lock();
        try {
            PaperPortfolio p = new PaperPortfolio();
            p.free.putAll(this.free);
            return p;
        } finally {
            lock.unlock();
        }
    }

    private void ensure(String asset, double needed) {
        double have = free.getOrDefault(asset, 0.0);
        if (have + 1e-9 < needed) {
            throw new IllegalStateException("Insufficient balance " + asset + ": need " + needed + ", have " + have);
        }
    }

    private void add(String asset, double delta) {
        free.put(asset, free.getOrDefault(asset, 0.0) + delta);
    }

    private void sub(String asset, double delta) {
        free.put(asset, free.getOrDefault(asset, 0.0) - delta);
    }

    private static String norm(String a) {
        return a == null ? "" : a.trim().toUpperCase();
    }
}
