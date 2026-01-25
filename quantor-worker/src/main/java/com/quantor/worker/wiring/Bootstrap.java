package com.quantor.worker.wiring;

import com.quantor.application.engine.LiveEngine;
import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.execution.JobScheduler;
import com.quantor.application.execution.impl.DefaultJobScheduler;
import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.ExchangePort;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
import com.quantor.application.lifecycle.BotStateManager;
import com.quantor.application.ports.ConfigPort;
import com.quantor.application.ports.NotifierPort;
import com.quantor.application.ports.PortfolioPort;
import com.quantor.application.ports.SymbolMetaPort;
import com.quantor.application.ports.TradeJournalPort;
import com.quantor.application.service.PipelineFactory;
import com.quantor.application.service.SessionService;
import com.quantor.application.usecase.TradingMode;
import com.quantor.application.usecase.TradingPipeline;
import com.quantor.domain.ai.AiStatsTracker;
import com.quantor.domain.risk.RiskManager;
import com.quantor.domain.strategy.AutoTuner;
import com.quantor.domain.strategy.Strategy;
import com.quantor.domain.strategy.impl.EmaCrossStrategy;
import com.quantor.domain.strategy.online.OnlineStrategy;
import com.quantor.exchange.BinanceClient;
import com.quantor.infrastructure.config.FileConfigService;
import com.quantor.infrastructure.exchange.BinanceExchangeAdapter;
import com.quantor.infrastructure.exchange.UnifiedBinanceExchangeAdapter;
import com.quantor.infrastructure.journal.SqliteTradeJournalAdapter;
import com.quantor.infrastructure.notification.ConsoleNotifier;
import com.quantor.infrastructure.notification.TelegramNotifier;
import com.quantor.infrastructure.paper.PaperBrokerState;
import com.quantor.infrastructure.paper.PaperOrderExecutionAdapter;
import com.quantor.infrastructure.paper.PaperPortfolioAdapter;
import com.quantor.infrastructure.paper.SymbolParserMetaAdapter;
import com.quantor.worker.util.JobParsing;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Bootstrap {

    private Bootstrap() {}

    /**
     * Loads config from working directory (config.properties + .env if present) and wires up a LiveEngine.
     */
    public static LiveEngine createLiveEngine() {
        try {
            ConfigPort config = FileConfigService.defaultFromWorkingDir();
            return createLiveEngine(config);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to load config from working directory. " +
                    "Make sure config.properties exists (or run the configure tool) and try again.",
                    e
            );
        }
    }

    /**
     * Wires up a LiveEngine using the provided ConfigPort.
     */
    public static LiveEngine createLiveEngine(ConfigPort config) {
        // Exchange
        BinanceClient client = new BinanceClient(config);
        BinanceExchangeAdapter legacy = new BinanceExchangeAdapter(client);
        ExchangePort exchange = new UnifiedBinanceExchangeAdapter(legacy);

        // Core settings (support both old + new keys)
        String rawSymbol = first(config, "trade.symbol", "symbol", "BTCUSDT");
        String rawInterval = first(config, "trade.interval", "interval", "1m");
        boolean realTradingEnabled = Boolean.parseBoolean(first(config, "liveRealTradingEnabled", "binance.testMode", "false"));

        MarketSymbol symbol = JobParsing.symbol(rawSymbol);
        Timeframe timeframe = JobParsing.timeframe(rawInterval);

        // Risk (old project keys)
        RiskManager riskManager = new RiskManager(
                config.getDouble("positionUSDT", 50.0),
                config.getDouble("feeRate", 0.001),
                config.getDouble("stopLossPct", 0.02),
                config.getDouble("takeProfitPct", 0.03)
        );

        // Strategy: online by default when strategyType=online
        Strategy strategy;
        String st = config.get("strategyType", "online").trim().toLowerCase();
        if ("online".equals(st)) {
            strategy = new OnlineStrategy(loadProfileProperties(config));
        } else {
            int emaFast = config.getInt("strategy.emaFast", 12);
            int emaSlow = config.getInt("strategy.emaSlow", 26);
            strategy = new EmaCrossStrategy(emaFast, emaSlow);
        }

        // AI / tuning (only when EMA)
        AiStatsTracker aiStats = new AiStatsTracker(config.get("ai.statsFile", "ai-stats.json"));
        int tuneEveryTrades = config.getInt("aiAutoTuneEveryTrades", 25);
        AutoTuner tuner = (strategy instanceof EmaCrossStrategy)
                ? new AutoTuner((EmaCrossStrategy) strategy, riskManager, tuneEveryTrades)
                : null;

        // Lifecycle + notifications
        BotStateManager stateManager = new BotStateManager();
        NotifierPort notifier = createNotifier(config);

        // Drawdown params for LiveEngine signature
        double maxSessionDrawdownPct = config.getDouble("maxSessionDrawdownPct", 0.10);
        double disableRealTradingDrawdownPct = config.getDouble("disableRealTradingDrawdownPct", 0.03);

        return new LiveEngine(
                strategy,
                riskManager,
                exchange,
                symbol,
                timeframe,
                notifier,
                stateManager,
                realTradingEnabled,
                aiStats,
                maxSessionDrawdownPct,
                disableRealTradingDrawdownPct,
                tuner
        );
    }

    /**
     * New (v2) entrypoint: wires a SessionService with an execution scheduler and pipeline factory.
     */
    public static SessionService createSessionService(ConfigPort config) {
        NotifierPort notifier = createNotifier(config);
        TradeJournalPort journal = new SqliteTradeJournalAdapter();
        JobScheduler scheduler = new DefaultJobScheduler(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));

        PipelineFactory factory = job -> {
            // Market data from Binance (klines are public)
            BinanceClient client = new BinanceClient(config);
            BinanceExchangeAdapter legacy = new BinanceExchangeAdapter(client);
            ExchangePort marketDataExchange = new UnifiedBinanceExchangeAdapter(legacy);

            // In MVP, prefer PAPER execution unless explicitly enabled
            boolean realTradingEnabled = Boolean.parseBoolean(config.get("liveRealTradingEnabled", "false"));

            SymbolMetaPort meta = new SymbolParserMetaAdapter(config);
            PortfolioPort portfolio;
            ExchangePort exchange;

            if (realTradingEnabled) {
                // LIVE mode: execute on exchange; portfolio is a minimal paper snapshot for now
                portfolio = new PaperPortfolioAdapter(new PaperBrokerState(), config, meta);
                exchange = marketDataExchange;
            } else {
                // PAPER mode: simulated execution + portfolio
                PaperBrokerState state = new PaperBrokerState();
                portfolio = new PaperPortfolioAdapter(state, config, meta);
                PaperOrderExecutionAdapter paperExec = new PaperOrderExecutionAdapter(state, legacy, meta, config);

                exchange = new ExchangePort() {
                    @Override
                    public ExchangeId id() {
                        return ExchangeId.PAPER;
                    }

                    @Override
                    public java.util.List<com.quantor.domain.market.Candle> getCandles(MarketSymbol symbol, Timeframe timeframe, int limit) throws Exception {
                        return marketDataExchange.getCandles(symbol, timeframe, limit);
                    }

                    @Override
                    public void marketBuy(MarketSymbol symbol, double quantity) throws Exception {
                        paperExec.marketBuy(JobParsing.toBinanceSymbol(symbol), quantity);
                    }

                    @Override
                    public void marketSell(MarketSymbol symbol, double quantity) throws Exception {
                        paperExec.marketSell(JobParsing.toBinanceSymbol(symbol), quantity);
                    }
                };
            }

            RiskManager risk = new RiskManager(
                    config.getDouble("positionUSDT", 50.0),
                    config.getDouble("feeRate", 0.001),
                    config.getDouble("stopLossPct", 0.02),
                    config.getDouble("takeProfitPct", 0.03)
            );

            Strategy strategy;
            String st = config.get("strategyType", "online").trim().toLowerCase();
            if ("online".equals(st)) {
                strategy = new OnlineStrategy(loadProfileProperties(config));
            } else {
                int emaFast = config.getInt("strategy.emaFast", 12);
                int emaSlow = config.getInt("strategy.emaSlow", 26);
                strategy = new EmaCrossStrategy(emaFast, emaSlow);
            }

            TradingMode mode = realTradingEnabled ? TradingMode.LIVE : TradingMode.PAPER;
            return new TradingPipeline(mode, exchange, portfolio, meta, strategy, risk, journal, notifier);
        };

        return new SessionService(factory, scheduler, notifier);
    }

    private static NotifierPort createNotifier(ConfigPort config) {
        boolean telegramEnabled = Boolean.parseBoolean(config.get("telegram.enabled", "false"));
        if (telegramEnabled) {
            String token = config.getSecret("telegram.botToken");
            String chatId = config.getSecret("telegram.chatId");
            if (token != null && !token.isBlank() && chatId != null && !chatId.isBlank()) {
                return new TelegramNotifier(token, chatId);
            }
        }
        return new ConsoleNotifier();
    }

    private static String first(ConfigPort cfg, String primary, String fallback, String def) {
        String v = cfg.get(primary, null);
        if (v == null || v.isBlank()) v = cfg.get(fallback, null);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static Properties loadProfileProperties(ConfigPort config) {
        Properties p = new Properties();
        try {
            if (config instanceof FileConfigService f) {
                Path file = f.getProfileDir().resolve("config.properties");
                if (Files.exists(file)) {
                    try (FileInputStream in = new FileInputStream(file.toFile())) {
                        p.load(in);
                    }
                }
            }
        } catch (Exception ignore) {}
        // Ensure mode is present for OnlineStrategy
        p.setProperty("mode", config.get("mode", p.getProperty("mode", "TEST")));
        return p;
    }
}
