package com.quantor.application.ports;

import com.quantor.domain.trading.SessionId;
import com.quantor.domain.trading.TradingSession;
import com.quantor.domain.trading.UserId;

import java.util.Optional;

public interface TradingSessionRepository {
    Optional<TradingSession> findById(SessionId id);

    Optional<TradingSession> findActiveByUser(UserId userId);

    void save(TradingSession session);
}
