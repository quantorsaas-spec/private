package com.quantor.cli.bootstrap;

import com.quantor.application.engine.LiveEngine;
import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.execution.JobScheduler;
import com.quantor.application.execution.impl.DefaultJobScheduler;
import com.quantor.application.lifecycle.BotStateManager;
import com.quantor.application.ports.ConfigPort;
import com.quantor.application.ports.NotifierPort;
import com.quantor.application.ports.PortfolioPort;
import com.quantor.application.ports.SymbolMetaPort;
import com.quantor.application.ports.TradeJournalPort;
import com.quantor.application.service.PipelineFactory;
import com.quantor.application.service.SessionService;
import com.quantor.application.usecase.OrderCooldownGuard;
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
import com.quantor.infrastructure.exchange.PaperExchangeAdapter;
import com.quantor.infrastructure.exchange.SimpleExchangeRegistry;
import com.quantor.infrastructure.exchange.UnifiedBinanceExchangeAdapter;
import com.quantor.infrastructure.exchange.UnifiedBybitExchangeAdapter;
import com.quantor.infrastructure.exchange.UnifiedOkxExchangeAdapter;
import com.quantor.infrastructure.journal.SqliteTradeJournalAdapter;
import com.quantor.infrastructure.notification.ConsoleNotifier;
import com.quantor.infrastructure.notification.TelegramNotifier;
import com.quantor.infrastructure.paper.PaperBrokerState;
import com.quantor.infrastructure.paper.PaperPortfolioAdapter;
import com.quantor.infrastructure.paper.SymbolParserMetaAdapter;

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
        // Exchange clients
        BinanceClient client = new BinanceClient(config);
        com.quantor.exchange.BybitClient bybitClient = new com.quantor.exchange.BybitClient(config);
        com.quantor.exchange.OkxClient okxClient = new com.quantor.exchange.OkxClient(config);
        com.quantor.exchange.CoinbaseClient coinbaseClient = new com.quantor.exchange.CoinbaseClient(config);

        // Build exchange adapters
        com.quantor.application.exchange.ExchangePort binance = new UnifiedBinanceExchangeAdapter(new BinanceExchangeAdapter(client));
        com.quantor.application.exchange.ExchangePort bybit = new UnifiedBybitExchangeAdapter(new com.quantor.infrastructure.exchange.BybitExchangeAdapter(bybitClient));
        com.quantor.application.exchange.ExchangePort okx = new UnifiedOkxExchangeAdapter(new com.quantor.infrastructure.exchange.OkxExchangeAdapter(okxClient));
        com.quantor.application.exchange.ExchangePort coinbase = new com.quantor.infrastructure.exchange.UnifiedCoinbaseExchangeAdapter(
                new com.quantor.infrastructure.exchange.CoinbaseExchangeAdapter(coinbaseClient)
        );

        // Registry holds only real exchanges
        com.quantor.application.exchange.ExchangeRegistry registry = new SimpleExchangeRegistry()
                .register(binance)
                .register(bybit)
                .register(okx)
                .register(coinbase);

        // Core settings
        String rawExchange = first(config, "trade.exchange", "exchange", "BINANCE");
        com.quantor.application.exchange.ExchangeId exchangeId =
                com.quantor.application.exchange.ExchangeId.valueOf(rawExchange.trim().toUpperCase());

        com.quantor.application.exchange.MarketSymbol symbol =
                com.quantor.application.exchange.MarketSymbol.parse(first(config, "trade.symbol", "symbol", "BTC/USDT"));
        com.quantor.application.exchange.Timeframe timeframe =
                com.quantor.application.exchange.Timeframes.parse(first(config, "trade.interval", "interval", "1m"));

        com.quantor.application.exchange.ExchangePort selectedExchange;
        if (exchangeId == com.quantor.application.exchange.ExchangeId.PAPER) {
            String rawMd = first(config, "paper.marketDataExchange", "paper.marketDataExchange", "BINANCE");
            com.quantor.application.exchange.ExchangeId md =
                    com.quantor.application.exchange.ExchangeId.valueOf(rawMd.trim().toUpperCase());
            selectedExchange = new PaperExchangeAdapter(registry.get(md));
        } else {
            selectedExchange = registry.get(exchangeId);
        }

        boolean realTradingEnabled = Boolean.parseBoolean(first(config, "liveRealTradingEnabled", "binance.testMode", "false"));

        RiskManager riskManager = new RiskManager(
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

        AiStatsTracker aiStats = new AiStatsTracker(config.get("ai.statsFile", "ai-stats.json"));
        int tuneEveryTrades = config.getInt("aiAutoTuneEveryTrades", 25);
        AutoTuner tuner = (strategy instanceof EmaCrossStrategy)
                ? new AutoTuner((EmaCrossStrategy) strategy, riskManager, tuneEveryTrades)
                : null;

        BotStateManager stateManager = new BotStateManager();
        NotifierPort notifier = createNotifier(config);

        double maxSessionDrawdownPct = config.getDouble("maxSessionDrawdownPct", 0.10);
        double disableRealTradingDrawdownPct = config.getDouble("disableRealTradingDrawdownPct", 0.03);

        return new LiveEngine(
                strategy,
                riskManager,
                selectedExchange,
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

        // IMPORTANT: this flag affects only "mode" label (LIVE/PAPER) and exchange selection behavior.
        boolean realTradingEnabled = Boolean.parseBoolean(first(config, "liveRealTradingEnabled", "binance.testMode", "false"));

        PipelineFactory factory = job -> {
            // Clients
            BinanceClient client = new BinanceClient(config);
            com.quantor.exchange.BybitClient bybitClient = new com.quantor.exchange.BybitClient(config);
            com.quantor.exchange.OkxClient okxClient = new com.quantor.exchange.OkxClient(config);
            com.quantor.exchange.CoinbaseClient coinbaseClient = new com.quantor.exchange.CoinbaseClient(config);

            // Exchanges
            com.quantor.application.exchange.ExchangePort binance = new UnifiedBinanceExchangeAdapter(new BinanceExchangeAdapter(client));
            com.quantor.application.exchange.ExchangePort bybit = new UnifiedBybitExchangeAdapter(new com.quantor.infrastructure.exchange.BybitExchangeAdapter(bybitClient));
            com.quantor.application.exchange.ExchangePort okx = new UnifiedOkxExchangeAdapter(new com.quantor.infrastructure.exchange.OkxExchangeAdapter(okxClient));
            com.quantor.application.exchange.ExchangePort coinbase = new com.quantor.infrastructure.exchange.UnifiedCoinbaseExchangeAdapter(
                    new com.quantor.infrastructure.exchange.CoinbaseExchangeAdapter(coinbaseClient)
            );

            com.quantor.application.exchange.ExchangeRegistry registry = new SimpleExchangeRegistry()
                    .register(binance)
                    .register(bybit)
                    .register(okx)
                    .register(coinbase);

            com.quantor.application.exchange.ExchangePort selectedExchange;
            if (job.exchange() == com.quantor.application.exchange.ExchangeId.PAPER) {
                selectedExchange = new PaperExchangeAdapter(registry.get(job.marketDataExchange()));
            } else {
                selectedExchange = registry.get(job.exchange());
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

            // Portfolio/meta
            SymbolMetaPort meta = new SymbolParserMetaAdapter(config);
            PaperBrokerState brokerState = new PaperBrokerState();
            PortfolioPort portfolio = new PaperPortfolioAdapter(brokerState, config, meta);

            // Exchange used by pipeline
            com.quantor.application.exchange.ExchangePort exchange = selectedExchange;

            TradingMode mode = realTradingEnabled ? TradingMode.LIVE : TradingMode.PAPER;

            // STOP-FIX wiring (MVP defaults)
com.quantor.application.ports.SubscriptionPort subscription = null;

boolean tradingEnabled = Boolean.parseBoolean(config.get("trading.enabled", "true"));
String disabledReason = config.get("trading.disabledReason", "Trading disabled");
com.quantor.application.ports.TradingControlPort control =
        new com.quantor.infrastructure.control.ConfigTradingControlPort(tradingEnabled, disabledReason);


// Anti double-order cooldown (0 = disabled)
int cooldownSeconds = config.getInt("trading.orderCooldownSeconds", 0);
OrderCooldownGuard cooldown = (cooldownSeconds > 0) ? new OrderCooldownGuard(cooldownSeconds) : null;


            String uid = config.get("userId", "local");

            return new TradingPipeline(
                    mode, exchange, portfolio, meta, strategy, risk, journal, notifier,
                    subscription, control, cooldown, uid
            );
        };

        return new SessionService(factory, scheduler, notifier, config);
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
