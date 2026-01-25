package com.quantor.application.usecase;

import com.quantor.application.exchange.ExchangePort;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
import com.quantor.application.ports.NotifierPort;
import com.quantor.application.ports.PortfolioPort;
import com.quantor.application.ports.SymbolMetaPort;
import com.quantor.application.ports.TradeJournalPort;
import com.quantor.domain.market.Candle;
import com.quantor.domain.order.TradeAction;
import com.quantor.domain.portfolio.Fill;
import com.quantor.domain.portfolio.PortfolioPosition;
import com.quantor.domain.portfolio.PortfolioSnapshot;
import com.quantor.domain.risk.RiskManager;
import com.quantor.domain.strategy.Strategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Unified use-case pipeline: MarketData -> Strategy -> Risk -> Execution -> Portfolio -> Notify.
 * Minimal MVP with long-only market orders.
 */
public class TradingPipeline {

    private final TradingMode mode;
    private final ExchangePort exchange;
    private final PortfolioPort portfolio;
    private final SymbolMetaPort symbolMeta;
    private final Strategy strategy;
    private final RiskManager riskManager;
    private final NotifierPort notifier;
    private final TradeJournalPort journal;

    public TradingPipeline(TradingMode mode,
                           ExchangePort exchange,
                           PortfolioPort portfolio,
                           SymbolMetaPort symbolMeta,
                           Strategy strategy,
                           RiskManager riskManager,
                           TradeJournalPort journal,
                           NotifierPort notifier) {
        this.mode = mode;
        this.exchange = exchange;
        this.portfolio = portfolio;
        this.symbolMeta = symbolMeta;
        this.strategy = strategy;
        this.riskManager = riskManager;
        this.journal = journal;
        this.notifier = notifier;
    }

    public PipelineResult tick(MarketSymbol symbol, Timeframe timeframe, int lookback) {
        try {
            List<Candle> candles = exchange.getCandles(symbol, timeframe, lookback);
            if (candles == null || candles.size() < 5) {
                return new PipelineResult(symbol, TradeAction.HOLD, false, "Not enough candles");
            }

            double lastPrice = candles.get(candles.size() - 1).getClose();
            PortfolioSnapshot snap = portfolio.getSnapshot();
            double equity = snap.getEquityQuote().doubleValue();

            // (Optional) PipelineContext can be used by advanced strategies; not needed for current MVP.
            TradeAction action = strategy.decide(candles);

            // Simple position state from portfolio
            PortfolioPosition pos = portfolio.getPosition(symbol.asBaseQuote());
            double posQty = (pos == null) ? 0.0 : pos.getQtyBase().doubleValue();

            boolean executed = false;
            String msg = "HOLD";

            if (action == TradeAction.BUY && posQty <= 0.0) {
                double stopPrice = riskManager.calcStopPrice(lastPrice);
                double qty = riskManager.calcPositionSize(lastPrice, stopPrice, equity);
                if (qty > 0) {
                    exchange.marketBuy(symbol, qty);
                    // PAPER/BACKTEST adapters should have already updated portfolio inside orderExec,
                    // but we still emit a fill for completeness if portfolio is applyFill-enabled.
                    try {
                        portfolio.applyFill(new Fill(symbol.asBaseQuote(), Fill.Side.BUY,
                                BigDecimal.valueOf(qty),
                                BigDecimal.valueOf(lastPrice),
                                BigDecimal.ZERO,
                                Instant.now()));
                    } catch (Exception ignore) {}
                    executed = true;
                    msg = "BUY qty=" + qty;
                    notifier.send("🟢 " + mode + " " + symbol + " " + msg);
                    try {
                        journal.logTrade(String.valueOf(mode), symbol.asBaseQuote(), "BUY", lastPrice, qty, equity, msg);
                    } catch (Exception ignore) {}
                }
            } else if (action == TradeAction.SELL && posQty > 0.0) {
                exchange.marketSell(symbol, posQty);
                try {
                    portfolio.applyFill(new Fill(symbol.asBaseQuote(), Fill.Side.SELL,
                            BigDecimal.valueOf(posQty),
                            BigDecimal.valueOf(lastPrice),
                            BigDecimal.ZERO,
                            Instant.now()));
                } catch (Exception ignore) {}
                executed = true;
                msg = "SELL qty=" + posQty;
                notifier.send("🔴 " + mode + " " + symbol + " " + msg);
                try {
                    journal.logTrade(String.valueOf(mode), symbol.asBaseQuote(), "SELL", lastPrice, posQty, equity, msg);
                } catch (Exception ignore) {}
            } else {
                // no trade
            }

            return new PipelineResult(symbol, action, executed, msg);
        } catch (Exception e) {
            try { notifier.send("❌ PIPELINE " + symbol + ": " + e.getMessage()); } catch (Exception ignore) {}
            return new PipelineResult(symbol, TradeAction.HOLD, false, "error: " + e.getMessage());
        }
    }
}
