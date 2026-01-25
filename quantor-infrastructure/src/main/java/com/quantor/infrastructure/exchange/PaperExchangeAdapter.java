package com.quantor.infrastructure.exchange;

import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.ExchangePort;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
import com.quantor.domain.market.Candle;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * PAPER exchange adapter.
 *
 * <p>Goal: provide a safe simulator that behaves like an exchange adapter but never places real orders.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Delegates candle loading to a provided market-data adapter (e.g. Binance/OKX), because paper trading
 *       still needs market prices.</li>
 *   <li>Executes MARKET buys/sells using the last close price, applying configurable slippage + fees.</li>
 *   <li>Maintains a simple in-memory portfolio (thread-safe).</li>
 * </ul>
 *
 * <p>NOTE: Portfolio persistence (DB) can be added later when the core requires it.
 */
public final class PaperExchangeAdapter implements ExchangePort {

    private final ExchangePort marketData;
    private final PaperPortfolio portfolio;
    private final PaperExecutionModel executionModel;

    /**
     * Create PAPER adapter that uses {@code marketData} for candles.
     *
     * @param marketData any adapter that can fetch candles (no private keys required for most exchanges)
     */
    public PaperExchangeAdapter(ExchangePort marketData) {
        this(marketData,
                PaperPortfolio.withInitialBalance("USDT", 10_000.0),
                PaperExecutionModel.defaults());
    }

    public PaperExchangeAdapter(ExchangePort marketData, PaperPortfolio portfolio, PaperExecutionModel executionModel) {
        this.marketData = Objects.requireNonNull(marketData, "marketData");
        this.portfolio = Objects.requireNonNull(portfolio, "portfolio");
        this.executionModel = Objects.requireNonNull(executionModel, "executionModel");
    }

    @Override
    public ExchangeId id() {
        return ExchangeId.PAPER;
    }

    @Override
    public List<Candle> getCandles(MarketSymbol symbol, Timeframe timeframe, int limit) throws Exception {
        // Candle source is delegated. Paper trading doesn't need auth to load candles.
        return marketData.getCandles(symbol, timeframe, limit);
    }

    @Override
    public void marketBuy(MarketSymbol symbol, double quantity) throws Exception {
        Objects.requireNonNull(symbol, "symbol");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");

        double price = lastPrice(symbol);
        PaperFill fill = executionModel.buyFill(symbol, quantity, price);
        portfolio.apply(fill);
    }

    @Override
    public void marketSell(MarketSymbol symbol, double quantity) throws Exception {
        Objects.requireNonNull(symbol, "symbol");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");

        double price = lastPrice(symbol);
        PaperFill fill = executionModel.sellFill(symbol, quantity, price);
        portfolio.apply(fill);
    }

    /**
     * Returns the last close price for the given symbol.
     *
     * <p>For simulation this is sufficient. When you need higher realism, switch to using mid/mark/last
     * + order book impact, but keep it inside the PaperExecutionModel.
     */
    private double lastPrice(MarketSymbol symbol) throws Exception {
        List<Candle> candles = marketData.getCandles(symbol, Timeframe.M1, 1);
        if (candles == null || candles.isEmpty()) {
            throw new IllegalStateException("No candles returned for " + symbol);
        }
        Candle c = candles.get(candles.size() - 1);
        double close = c.close();
        if (close <= 0) throw new IllegalStateException("Invalid close price: " + close);
        return close;
    }

    // Exposed for debugging/testing in CLI.
    public PaperPortfolio snapshotPortfolio() {
        return portfolio.copy();
    }

    public record PaperFill(
            Instant time,
            MarketSymbol symbol,
            Side side,
            double quantity,
            double price,
            double notional,
            double fee,
            double slippage
    ) {
        public enum Side { BUY, SELL }
    }
}
