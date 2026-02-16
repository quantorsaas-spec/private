package com.quantor.api.wiring;

import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.execution.JobScheduler;
import com.quantor.application.execution.impl.DefaultJobScheduler;
import com.quantor.application.ports.*;
import com.quantor.application.service.PipelineFactory;
import com.quantor.application.service.SessionService;
import com.quantor.application.usecase.OrderCooldownGuard;
import com.quantor.application.usecase.TradingMode;
import com.quantor.application.usecase.TradingPipeline;
import com.quantor.application.exchange.*;
import com.quantor.domain.risk.RiskManager;
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
import com.quantor.infrastructure.paper.*;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Bootstrap {

    private Bootstrap() {}

    /**
     * Backward compatible signature (your QuantorWiringConfig expects this).
     * If subscription is null, will fallback to fail-closed dev adapter.
     */
    public static SessionService createSessionService(ConfigPort config, SubscriptionPort subscription) {

        NotifierPort notifier = createNotifier(config);
        TradeJournalPort journal = new SqliteTradeJournalAdapter();
        JobScheduler scheduler = new DefaultJobScheduler(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
        );

        // If wiring didn't provide a SubscriptionPort => fail-closed fallback (MVP)
        SubscriptionPort sub = (subscription != null) ? subscription : devSubscription(config);

        PipelineFactory factory = (ExecutionJob job) -> {

            BinanceClient client = new BinanceClient(config);
            BinanceExchangeAdapter legacy = new BinanceExchangeAdapter(client);
            ExchangePort liveExchange = new UnifiedBinanceExchangeAdapter(legacy);

            boolean realTradingEnabled =
                    Boolean.parseBoolean(config.get("liveRealTradingEnabled", "false"));

            SymbolMetaPort meta = new SymbolParserMetaAdapter(config);
            PortfolioPort portfolio =
                    new PaperPortfolioAdapter(new PaperBrokerState(), config, meta);

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
                strategy = new EmaCrossStrategy(
                        config.getInt("strategy.emaFast", 12),
                        config.getInt("strategy.emaSlow", 26)
                );
            }

            TradingMode mode = realTradingEnabled ? TradingMode.LIVE : TradingMode.PAPER;

            TradingControlPort control = new TradingControlPort() {
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
            OrderCooldownGuard cooldown =
                    cooldownSeconds > 0 ? new OrderCooldownGuard(cooldownSeconds) : null;

            // MVP single-user: keep uid from config (later from auth context)
            String uid = config.get("userId", "local");

            return new TradingPipeline(
                    mode,
                    exchange,
                    portfolio,
                    meta,
                    strategy,
                    risk,
                    journal,
                    notifier,
                    sub,
                    control,
                    cooldown,
                    uid
            );
        };

        // IMPORTANT: pass subscription into SessionService (hard gate in start/resume)
        return new SessionService(factory, scheduler, notifier, config, sub);
    }

    /**
     * Convenience overload (if somewhere else calls 1-arg).
     */
    public static SessionService createSessionService(ConfigPort config) {
        return createSessionService(config, null);
    }

    /**
     * PAPER mode adapter: allow reading candles but block order placement.
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
        public java.util.List<com.quantor.domain.market.Candle> getCandles(
                MarketSymbol symbol,
                Timeframe timeframe,
                int limit
        ) throws Exception {
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

        p.setProperty("mode", config.get("mode", p.getProperty("mode", "TEST")));
        return p;
    }

    /**
     * MVP fail-closed dev subscription:
     * - if billing.forcePaid=true -> PAID
     * - else -> BLOCKED
     *
     * NOTE: No external deps. Keeps build stable.
     */
    private static SubscriptionPort devSubscription(ConfigPort config) {
        boolean forcePaid = Boolean.parseBoolean(config.get("billing.forcePaid", "false"));

        return new SubscriptionPort() {
            @Override
            public java.util.Optional<SubscriptionSnapshot> findLatest(com.quantor.domain.trading.UserId userId) {
                if (!forcePaid) {
                    return java.util.Optional.empty(); // => BLOCKED (fail-closed)
                }
                // "paid" snapshot for MVP
                java.util.UUID uid;
                try {
                    uid = java.util.UUID.fromString(userId.value());
                } catch (Exception e) {
                    // even if userId isn't UUID -> still fail closed, don't accidentally allow
                    return java.util.Optional.empty();
                }

                return java.util.Optional.of(
                        new SubscriptionSnapshot(
                                uid,
                                "PRO",
                                "ACTIVE",
                                false,
                                java.time.OffsetDateTime.now().plusDays(30)
                        )
                );
            }
        };
    }
}
