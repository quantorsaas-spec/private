package com.quantor.infrastructure.paper;

import com.quantor.application.ports.ConfigPort;
import com.quantor.application.ports.MarketDataPort;
import com.quantor.application.ports.OrderExecutionPort;
import com.quantor.application.ports.SymbolMetaPort;
import com.quantor.domain.market.Candle;
import com.quantor.domain.portfolio.PortfolioPosition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Paper order execution adapter: market orders with fee+slippage, multi-symbol portfolio.
 * Long-only.
 */
public class PaperOrderExecutionAdapter implements OrderExecutionPort {

    private final PaperBrokerState state;
    private final MarketDataPort marketData;
    private final SymbolMetaPort symbolMeta;
    private final ConfigPort cfg;

    private final String quoteAsset;
    private final BigDecimal feeBps;
    private final BigDecimal slippageBps;

    public PaperOrderExecutionAdapter(PaperBrokerState state, MarketDataPort marketData, SymbolMetaPort symbolMeta, ConfigPort cfg) {
        this.state = state;
        this.marketData = marketData;
        this.symbolMeta = symbolMeta;
        this.cfg = cfg;

        this.quoteAsset = cfg.get("paper.quoteAsset", "USDT").trim();
        this.feeBps = new BigDecimal(cfg.get("paper.feeBps", "10"));
        this.slippageBps = new BigDecimal(cfg.get("paper.slippageBps", "5"));

        state.balances().putIfAbsent(quoteAsset, new BigDecimal(cfg.get("paper.initialBalance." + quoteAsset, "1000")));
    }

    @Override
    public void marketBuy(String symbol, double quantity) throws Exception {
        if (quantity <= 0) return;
        SymbolMetaPort.SymbolMeta meta = symbolMeta.getMeta(symbol);
        if (!quoteAsset.equalsIgnoreCase(meta.quoteAsset())) {
            throw new IllegalArgumentException("Paper broker quoteAsset mismatch: expected " + quoteAsset + " but symbol quote is " + meta.quoteAsset());
        }

        double last = lastPrice(symbol);
        state.setLastPrice(symbol, last);

        BigDecimal qty = BigDecimal.valueOf(quantity);
        BigDecimal price = BigDecimal.valueOf(last).multiply(onePlusBps(slippageBps)); // worse price for buy
        BigDecimal notional = qty.multiply(price);

        BigDecimal fee = notional.multiply(feeBps).divide(BigDecimal.valueOf(10000), 12, RoundingMode.HALF_UP);
        BigDecimal cost = notional.add(fee);

        synchronized (state) {
            BigDecimal quoteBal = state.balances().getOrDefault(quoteAsset, BigDecimal.ZERO);
            if (quoteBal.compareTo(cost) < 0) {
                throw new IllegalStateException("Insufficient " + quoteAsset + " balance for BUY. Need=" + cost + " have=" + quoteBal);
            }
            state.balances().put(quoteAsset, quoteBal.subtract(cost));

            // credit base asset balance too (optional)
            state.balances().merge(meta.baseAsset(), qty, BigDecimal::add);

            PortfolioPosition pos = state.positions().computeIfAbsent(symbol, s -> new PortfolioPosition(s, meta.baseAsset(), meta.quoteAsset()));
            // update avg entry price (VWAP)
            BigDecimal oldQty = pos.getQtyBase();
            BigDecimal newQty = oldQty.add(qty);
            if (newQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal oldCost = pos.getAvgEntryPrice().multiply(oldQty);
                BigDecimal newCost = oldCost.add(price.multiply(qty));
                pos.setAvgEntryPrice(newCost.divide(newQty, 12, RoundingMode.HALF_UP));
            }
            pos.setQtyBase(newQty);
        }
    }

    @Override
    public void marketSell(String symbol, double quantity) throws Exception {
        if (quantity <= 0) return;
        SymbolMetaPort.SymbolMeta meta = symbolMeta.getMeta(symbol);
        if (!quoteAsset.equalsIgnoreCase(meta.quoteAsset())) {
            throw new IllegalArgumentException("Paper broker quoteAsset mismatch: expected " + quoteAsset + " but symbol quote is " + meta.quoteAsset());
        }

        double last = lastPrice(symbol);
        state.setLastPrice(symbol, last);

        BigDecimal qty = BigDecimal.valueOf(quantity);
        BigDecimal price = BigDecimal.valueOf(last).multiply(oneMinusBps(slippageBps)); // worse price for sell
        BigDecimal notional = qty.multiply(price);
        BigDecimal fee = notional.multiply(feeBps).divide(BigDecimal.valueOf(10000), 12, RoundingMode.HALF_UP);
        BigDecimal proceeds = notional.subtract(fee);

        synchronized (state) {
            PortfolioPosition pos = state.positions().get(symbol);
            if (pos == null || pos.getQtyBase().compareTo(qty) < 0) {
                throw new IllegalStateException("Insufficient position for SELL " + symbol + " qty=" + qty);
            }

            // update balances
            state.balances().merge(quoteAsset, proceeds, BigDecimal::add);
            state.balances().merge(meta.baseAsset(), qty.negate(), BigDecimal::add);

            // realized pnl
            BigDecimal pnl = price.subtract(pos.getAvgEntryPrice()).multiply(qty).subtract(fee);
            pos.addRealizedPnlQuote(pnl);

            BigDecimal newQty = pos.getQtyBase().subtract(qty);
            pos.setQtyBase(newQty);

            if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                // close position
                state.positions().remove(symbol);
            }
        }
    }

    private double lastPrice(String symbol) throws Exception {
        List<Candle> c = marketData.getCandles(symbol, "1m", 2);
        if (c == null || c.isEmpty()) throw new IllegalStateException("No candles for " + symbol);
        return c.get(c.size() - 1).getClose();
    }

    private static BigDecimal onePlusBps(BigDecimal bps) {
        return BigDecimal.ONE.add(bps.divide(BigDecimal.valueOf(10000), 12, RoundingMode.HALF_UP));
    }
    private static BigDecimal oneMinusBps(BigDecimal bps) {
        return BigDecimal.ONE.subtract(bps.divide(BigDecimal.valueOf(10000), 12, RoundingMode.HALF_UP));
    }
}
