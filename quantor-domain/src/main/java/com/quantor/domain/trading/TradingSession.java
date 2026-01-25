package com.quantor.domain.trading;

import java.time.Instant;
import java.util.Objects;

public final class TradingSession {

    private final SessionId id;
    private final UserId userId;
    private final ExchangeAccountRef exchangeAccountRef;
    private final StrategyId strategyId;

    private TradingStatus status;
    private Instant startedAt;
    private Instant stoppedAt;
    private StopReason stopReason;
    private PnLSnapshot pnl;

    private TradingSession(SessionId id,
                           UserId userId,
                           ExchangeAccountRef exchangeAccountRef,
                           StrategyId strategyId) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.exchangeAccountRef = Objects.requireNonNull(exchangeAccountRef, "exchangeAccountRef");
        this.strategyId = Objects.requireNonNull(strategyId, "strategyId");
        this.status = TradingStatus.IDLE;
        this.pnl = PnLSnapshot.zero();
    }

    public static TradingSession create(UserId userId, ExchangeAccountRef ref, StrategyId strategyId) {
        return new TradingSession(SessionId.newId(), userId, ref, strategyId);
    }

    public void start() {
        if (status == TradingStatus.RUNNING) {
            throw new SessionAlreadyRunningException("Session already RUNNING: " + id.value());
        }
        if (status == TradingStatus.STOPPED || status == TradingStatus.ERROR) {
            throw new InvalidSessionTransitionException("Cannot start finished session: " + status);
        }
        this.startedAt = Instant.now();
        this.status = TradingStatus.RUNNING;
        this.stopReason = null;
        this.stoppedAt = null;
    }

    public void requestStop(StopReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (status != TradingStatus.RUNNING) {
            throw new InvalidSessionTransitionException("Cannot stop when status=" + status);
        }
        this.stopReason = reason;
        this.stoppedAt = Instant.now();
        this.status = TradingStatus.STOPPED;
    }

    public void markError(StopReason reason) {
        Objects.requireNonNull(reason, "reason");
        this.stopReason = reason;
        this.stoppedAt = Instant.now();
        this.status = TradingStatus.ERROR;
    }

    public void updatePnl(PnLSnapshot pnl) {
        this.pnl = Objects.requireNonNull(pnl, "pnl");
    }

    public SessionId id() { return id; }
    public UserId userId() { return userId; }
    public ExchangeAccountRef exchangeAccountRef() { return exchangeAccountRef; }
    public StrategyId strategyId() { return strategyId; }

    public TradingStatus status() { return status; }
    public Instant startedAt() { return startedAt; }
    public Instant stoppedAt() { return stoppedAt; }
    public StopReason stopReason() { return stopReason; }
    public PnLSnapshot pnl() { return pnl; }
    /**
     * Rehydrate session from persistence.
     * Keep domain invariants: status is authoritative, timestamps may be null depending on state.
     */
    public static TradingSession restore(SessionId id,
                                        UserId userId,
                                        ExchangeAccountRef ref,
                                        StrategyId strategyId,
                                        TradingStatus status,
                                        Instant startedAt,
                                        Instant stoppedAt,
                                        StopReason stopReason,
                                        PnLSnapshot pnl) {
        TradingSession s = new TradingSession(Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(userId, "userId"),
                Objects.requireNonNull(ref, "ref"),
                Objects.requireNonNull(strategyId, "strategyId"));
        s.status = Objects.requireNonNull(status, "status");
        s.startedAt = startedAt;
        s.stoppedAt = stoppedAt;
        s.stopReason = stopReason;
        s.pnl = (pnl == null) ? PnLSnapshot.zero() : pnl;
        return s;
    }
}
