package com.quantor.worker.wiring;

import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.ExchangePort;
import com.quantor.application.ports.ConfigPort;
import com.quantor.application.ports.NotifierPort;
import com.quantor.application.ports.PortfolioPort;
import com.quantor.application.ports.SymbolMetaPort;
import com.quantor.application.ports.TradeJournalPort;
import com.quantor.application.service.PipelineFactory;
import com.quantor.application.usecase.TradingMode;
import com.quantor.application.usecase.TradingPipeline;
import com.quantor.domain.risk.RiskManager;
import com.quantor.domain.strategy.Strategy;
import com.quantor.exchange.BinanceClient;
import com.quantor.infrastructure.exchange.BinanceExchangeAdapter;
import com.quantor.infrastructure.exchange.UnifiedBinanceExchangeAdapter;
import com.quantor.infrastructure.paper.PaperBrokerState;
import com.quantor.infrastructure.paper.PaperOrderExecutionAdapter;
import com.quantor.infrastructure.paper.PaperPortfolioAdapter;
import com.quantor.infrastructure.paper.SymbolParserMetaAdapter;
import com.quantor.worker.util.JobParsing;

/**
 * Worker-specific pipeline factory.
 *
 * NOTE: This wiring is intentionally minimal; SaaS orchestration lives in the saas modules.
 */
public final class SaasPipelineFactory implements PipelineFactory {

  private final ConfigPort config;
  private final NotifierPort notifier;
  private final TradeJournalPort journal;

  public SaasPipelineFactory(ConfigPort config, NotifierPort notifier, TradeJournalPort journal) {
    this.config = config;
    this.notifier = notifier;
    this.journal = journal;
  }

  @Override
  public TradingPipeline create(ExecutionJob job) {
    BinanceClient client = new BinanceClient(config);
    BinanceExchangeAdapter legacy = new BinanceExchangeAdapter(client);
    ExchangePort marketData = new UnifiedBinanceExchangeAdapter(legacy);

    SymbolMetaPort meta = new SymbolParserMetaAdapter(config);

    TradingMode mode;
    PortfolioPort portfolio;
    ExchangePort exchange;

    if (job.exchange() == ExchangeId.PAPER) {
      mode = TradingMode.PAPER;
      PaperBrokerState state = new PaperBrokerState();
      portfolio = new PaperPortfolioAdapter(state, config, meta);
      PaperOrderExecutionAdapter paperExec = new PaperOrderExecutionAdapter(state, legacy, meta, config);
      exchange = new ExchangePort() {
        @Override public ExchangeId id() { return ExchangeId.PAPER; }
        @Override public java.util.List<com.quantor.domain.market.Candle> getCandles(
            com.quantor.application.exchange.MarketSymbol symbol,
            com.quantor.application.exchange.Timeframe timeframe,
            int limit
        ) throws Exception {
          return marketData.getCandles(symbol, timeframe, limit);
        }
        @Override public void marketBuy(com.quantor.application.exchange.MarketSymbol symbol, double quantity) throws Exception {
          paperExec.marketBuy(JobParsing.toBinanceSymbol(symbol), quantity);
        }
        @Override public void marketSell(com.quantor.application.exchange.MarketSymbol symbol, double quantity) throws Exception {
          paperExec.marketSell(JobParsing.toBinanceSymbol(symbol), quantity);
        }
      };
    } else {
      mode = TradingMode.LIVE;
      portfolio = new PaperPortfolioAdapter(new PaperBrokerState(), config, meta);
      exchange = marketData;
    }

    RiskManager risk = new RiskManager(
        config.getDouble("positionUSDT", 50.0),
        config.getDouble("feeRate", 0.001),
        config.getDouble("stopLossPct", 0.02),
        config.getDouble("takeProfitPct", 0.03)
    );

    Strategy strategy = new com.quantor.domain.strategy.online.OnlineStrategy(new java.util.Properties());

   // STOP-FIX wiring (MVP defaults)
com.quantor.application.ports.SubscriptionPort subscription = null;

com.quantor.application.ports.TradingControlPort control = new com.quantor.application.ports.TradingControlPort() {
    @Override
    public boolean isTradingEnabled() {
        return Boolean.parseBoolean(config.get("trading.enabled", "true"));
    }
    @Override
    public String disabledReason() {
        return config.get("trading.disabledReason", "Trading disabled");
    }
};

int cooldownSeconds = config.getInt("trading.orderCooldownSeconds", 0);
com.quantor.application.usecase.OrderCooldownGuard cooldown =
        (cooldownSeconds > 0) ? new com.quantor.application.usecase.OrderCooldownGuard(cooldownSeconds) : null;

String uid = config.get("userId", "local");


return new TradingPipeline(
        mode, exchange, portfolio, meta, strategy, risk, journal, notifier,
        subscription, control, cooldown, uid
);

  }
}
