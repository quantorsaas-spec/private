package com.quantor.application.service;

import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
import com.quantor.application.guard.TradePermissionGuard;
import com.quantor.application.ports.TradingSessionRepository;
import com.quantor.domain.trading.ExchangeAccountRef;
import com.quantor.domain.trading.StrategyId;
import com.quantor.domain.trading.TradingSession;
import com.quantor.domain.trading.UserId;

import java.util.Objects;

/**
 * P0: One public entrypoint for LIVE trading start/stop with SaaS paywall + session persistence.
 *
 * Integrates with existing scheduler-based SessionService (engine/pipelines).
 */
public final class CoreTradingOrchestrator {

    private final TradePermissionGuard permissionGuard;
    private final TradingSessionRepository sessions;
    private final SessionService runtime; // existing scheduler-based runner

    // Core defaults (no user customisation in P0)
    private final ExchangeId exchangeId;
    private final MarketSymbol symbol;
    private final Timeframe timeframe;
    private final int lookback;
    private final long periodMs;

    public CoreTradingOrchestrator(TradePermissionGuard permissionGuard,
                                  TradingSessionRepository sessions,
                                  SessionService runtime,
                                  ExchangeId exchangeId,
                                  MarketSymbol symbol,
                                  Timeframe timeframe,
                                  int lookback,
                                  long periodMs) {
        this.permissionGuard = Objects.requireNonNull(permissionGuard, "permissionGuard");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.exchangeId = Objects.requireNonNull(exchangeId, "exchangeId");
        this.symbol = Objects.requireNonNull(symbol, "symbol");
        this.timeframe = Objects.requireNonNull(timeframe, "timeframe");
        this.lookback = lookback;
        this.periodMs = periodMs;
    }

    public TradingSession startLive(String userId, String accountId) {
        UserId uid = new UserId(userId);
        permissionGuard.assertCanStart(uid);

        sessions.findActiveByUser(uid).ifPresent(s ->
                { throw new IllegalStateException("Session already running for user: " + userId); });

        TradingSession session = TradingSession.create(uid,
                new ExchangeAccountRef(exchangeId.name(), accountId),
                StrategyId.EMA);

        session.start();
        sessions.save(session);

        ExecutionJob job = new ExecutionJob(
                uid.value(),
                session.strategyId().name(),
                exchangeId,
                exchangeId, // market data exchange = same for P0
                symbol,
                timeframe,
                lookback
        );

        runtime.start(job, periodMs);
        return session;
    }

    public void stopLive(String userId) {
        UserId uid = new UserId(userId);

        // Best-effort stop runtime job (even if persistence is missing)
        ExecutionJob job = new ExecutionJob(
                uid.value(),
                StrategyId.EMA.name(),
                exchangeId,
                exchangeId,
                symbol,
                timeframe,
                lookback
        );
        runtime.stop(job);

        sessions.findActiveByUser(uid).ifPresent(s -> {
            s.requestStop(com.quantor.domain.trading.StopReason.of(com.quantor.domain.trading.StopReasonCode.USER_REQUEST, "User requested stop"));
            sessions.save(s);
        });
    }

    public static CoreTradingOrchestrator defaults(TradePermissionGuard guard,
                                                  TradingSessionRepository sessions,
                                                  SessionService runtime) {
        return new CoreTradingOrchestrator(
                guard,
                sessions,
                runtime,
                ExchangeId.BINANCE,
                new MarketSymbol("BTC", "USDT"),
                Timeframe.M1,
                500,
                1000
        );
    }
}
