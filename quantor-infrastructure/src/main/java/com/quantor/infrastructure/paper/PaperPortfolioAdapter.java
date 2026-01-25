package com.quantor.infrastructure.paper;

import com.quantor.application.ports.ConfigPort;
import com.quantor.application.ports.PortfolioPort;
import com.quantor.application.ports.SymbolMetaPort;
import com.quantor.domain.portfolio.AssetBalance;
import com.quantor.domain.portfolio.Fill;
import com.quantor.domain.portfolio.PortfolioPosition;
import com.quantor.domain.portfolio.PortfolioSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Portfolio adapter for paper broker (multi-symbol).
 */
public class PaperPortfolioAdapter implements PortfolioPort {

    private final PaperBrokerState state;
    private final ConfigPort config;
    private final SymbolMetaPort meta;
    private final String quoteAsset;

    public PaperPortfolioAdapter(PaperBrokerState state, ConfigPort config, SymbolMetaPort meta) {
        this.state = state;
        this.config = config;
        this.meta = meta;
        this.quoteAsset = config.get("paper.quoteAsset", "USDT").trim();

        // init quote balance if missing
        state.balances().putIfAbsent(quoteAsset, new BigDecimal(config.get("paper.initialBalance." + quoteAsset, "1000")));
    }

    @Override
    public PortfolioSnapshot getSnapshot() {
        Map<String, AssetBalance> balances = new HashMap<>();
        for (Map.Entry<String, BigDecimal> e : state.balances().entrySet()) {
            balances.put(e.getKey(), new AssetBalance(e.getKey(), e.getValue(), BigDecimal.ZERO));
        }

        Map<String, PortfolioPosition> positions = new HashMap<>(state.positions());

        BigDecimal equity = balances.getOrDefault(quoteAsset, new AssetBalance(quoteAsset, BigDecimal.ZERO, BigDecimal.ZERO)).getFree();

        // equity from positions using cached last prices
        for (PortfolioPosition p : positions.values()) {
            Double px = state.getLastPrice(p.getSymbol());
            if (px == null) continue;
            equity = equity.add(p.getQtyBase().multiply(BigDecimal.valueOf(px)));
        }

        return new PortfolioSnapshot(balances, positions, equity, quoteAsset, Instant.now());
    }

    @Override
    public PortfolioPosition getPosition(String symbol) {
        return state.positions().get(symbol);
    }

    @Override
    public void applyFill(Fill fill) {
        try {
            SymbolMetaPort.SymbolMeta m = meta.getMeta(fill.getSymbol());
            String base = m.baseAsset();
            String quote = m.quoteAsset();

            BigDecimal qty = fill.getQtyBase();
            BigDecimal price = fill.getPrice();
            BigDecimal fee = fill.getFeeQuote() == null ? BigDecimal.ZERO : fill.getFeeQuote();

            // Ensure balances exist
            state.balances().putIfAbsent(base, BigDecimal.ZERO);
            state.balances().putIfAbsent(quote, BigDecimal.ZERO);

            BigDecimal baseBal = state.balances().get(base);
            BigDecimal quoteBal = state.balances().get(quote);
            BigDecimal cost = qty.multiply(price);

            if (fill.getSide() == Fill.Side.BUY) {
                // Spend quote, receive base
                state.balances().put(quote, quoteBal.subtract(cost).subtract(fee));
                state.balances().put(base, baseBal.add(qty));
            } else {
                // Spend base, receive quote
                state.balances().put(base, baseBal.subtract(qty));
                state.balances().put(quote, quoteBal.add(cost).subtract(fee));
            }

            // Update position snapshot for the symbol
            PortfolioPosition current = state.positions().get(fill.getSymbol());
            BigDecimal posQty = (current == null) ? BigDecimal.ZERO : current.getQtyBase();

            BigDecimal newQty = (fill.getSide() == Fill.Side.BUY) ? posQty.add(qty) : posQty.subtract(qty);
            PortfolioPosition updated = (current == null)
                    ? new PortfolioPosition(fill.getSymbol(), base, quote)
                    : current;
            updated.setQtyBase(newQty);
            state.positions().put(fill.getSymbol(), updated);

            // Update last price cache for equity calculation
            state.setLastPrice(fill.getSymbol(), price.doubleValue());
        } catch (Exception ignore) {
            // best-effort
        }
    }
}
