package com.quantor.application.ports.impl;

import com.quantor.application.ports.TradingSessionRepository;
import com.quantor.domain.trading.SessionId;
import com.quantor.domain.trading.TradingSession;
import com.quantor.domain.trading.TradingStatus;
import com.quantor.domain.trading.UserId;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev-only repository. Replace with DB-backed implementation in infrastructure.
 */
public final class InMemoryTradingSessionRepository implements TradingSessionRepository {

    private final ConcurrentHashMap<String, TradingSession> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<TradingSession> findById(SessionId id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id.value()));
    }

    @Override
    public Optional<TradingSession> findActiveByUser(UserId userId) {
        if (userId == null) return Optional.empty();
        return byId.values().stream()
                .filter(s -> s.userId().equals(userId))
                .filter(s -> s.status() == TradingStatus.RUNNING)
                .findFirst();
    }

    @Override
    public void save(TradingSession session) {
        if (session == null) return;
        byId.put(session.id().value(), session);
    }
}
