package com.quantor.api.wiring;

import com.quantor.application.engine.LiveEngine;
import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.execution.JobScheduler;
import com.quantor.application.execution.impl.DefaultJobScheduler;
import com.quantor.application.lifecycle.BotStateManager;
import com.quantor.application.ports.*;
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
import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.ExchangePort;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
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

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Locale;


public final class Bootstrap {

    private Bootstrap() {
    }

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
        // Exchange (unified port expected by application layer)
        BinanceClient client = new BinanceClient(config);
        BinanceExchangeAdapter legacy = new BinanceExchangeAdapter(client);
        ExchangePort exchange = new UnifiedBinanceExchangeAdapter(legacy);

        // Core settings (support both old + new keys)
        String symbolRaw = first(config, "trade.symbol", "symbol", "BTCUSDT");
        String intervalRaw = first(config, "trade.interval", "interval", "1m");
        MarketSymbol symbol = parseSymbol(symbolRaw);
        Timeframe interval = parseTimeframe(intervalRaw);
        boolean realTradingEnabled = Boolean.parseBoolean(first(config, "liveRealTradingEnabled", "binance.testMode", "false"));

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
            interval,
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

        PipelineFactory factory = new PipelineFactory() {
            @Override
            public TradingPipeline create(ExecutionJob job) {
                // Market data from Binance (klines are public). For PAPER mode we still use live market data
                // but we do NOT send real orders.
                BinanceClient client = new BinanceClient(config);
                BinanceExchangeAdapter legacy = new BinanceExchangeAdapter(client);
                ExchangePort liveExchange = new UnifiedBinanceExchangeAdapter(legacy);

                // In MVP, prefer PAPER execution unless explicitly enabled
                boolean realTradingEnabled = Boolean.parseBoolean(config.get("liveRealTradingEnabled", "false"));

                SymbolMetaPort meta = new SymbolParserMetaAdapter(config);

                // Keep a local portfolio snapshot (journal records trades; later we can persist this per-user)
                PortfolioPort portfolio = new PaperPortfolioAdapter(new PaperBrokerState(), config, meta);

                ExchangePort exchange = realTradingEnabled
                        ? liveExchange
                        : new MarketDataOnlyExchange(liveExchange);

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
            }
        };

        return new SessionService(factory, scheduler, notifier);
    }

    /**
     * PAPER mode adapter: allow reading candles from a real exchange adapter but block order placement.
     */
    private static final class MarketDataOnlyExchange implements ExchangePort {
        private final ExchangePort delegate;

        private MarketDataOnlyExchange(ExchangePort delegate) {
            this.delegate = delegate;
        }

        @Override
        public ExchangeId id() {
            return delegate.id();
        }

        @Override
        public java.util.List<com.quantor.domain.market.Candle> getCandles(MarketSymbol symbol, Timeframe timeframe, int limit) throws Exception {
            return delegate.getCandles(symbol, timeframe, limit);
        }

        @Override
        public void marketBuy(MarketSymbol symbol, double quantity) {
            throw new IllegalStateException("PAPER mode: order placement is disabled");
        }

        @Override
        public void marketSell(MarketSymbol symbol, double quantity) {
            throw new IllegalStateException("PAPER mode: order placement is disabled");
        }
    }

    private static MarketSymbol parseSymbol(String raw) {
        if (raw == null || raw.isBlank()) return MarketSymbol.parse("BTC/USDT");
        String s = raw.trim().toUpperCase(java.util.Locale.ROOT);
        // Accept formats: BTC/USDT, BTC-USDT, BTCUSDT
        if (s.contains("/") || s.contains("-")) {
            return MarketSymbol.parse(s);
        }
        // Heuristic split for common quote assets
        for (String quote : new String[]{"USDT","USDC","BUSD","USD","BTC","ETH"}) {
            if (s.endsWith(quote) && s.length() > quote.length()) {
                String base = s.substring(0, s.length() - quote.length());
                return MarketSymbol.parse(base + "/" + quote);
            }
        }
        // Last resort: assume USDT
        return MarketSymbol.parse(s + "/USDT");
    }

    private static Timeframe parseTimeframe(String raw) {
        if (raw == null || raw.isBlank()) return Timeframe.M1;
        String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (s) {
            case "1m", "m1" -> Timeframe.M1;
            // Quantor MVP supports only the universal set in Timeframe enum.
            // Map 3m requests to the closest supported timeframe.
            case "3m", "m3" -> Timeframe.M5;
            case "5m", "m5" -> Timeframe.M5;
            case "15m", "m15" -> Timeframe.M15;
            case "30m", "m30" -> Timeframe.M30;
            case "1h", "h1" -> Timeframe.H1;
            case "4h", "h4" -> Timeframe.H4;
            case "1d", "d1" -> Timeframe.D1;
            default -> Timeframe.valueOf(s.toUpperCase(java.util.Locale.ROOT));
        };
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
