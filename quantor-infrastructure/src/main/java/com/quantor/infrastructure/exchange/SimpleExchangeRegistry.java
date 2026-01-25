package com.quantor.infrastructure.exchange;

import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.ExchangePort;
import com.quantor.application.exchange.ExchangeRegistry;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Simple in-memory exchange registry.
 *
 * For a Spring/SaaS setup this can be replaced by DI.
 */
public final class SimpleExchangeRegistry implements ExchangeRegistry {

    private final Map<ExchangeId, ExchangePort> map = new EnumMap<>(ExchangeId.class);

    public SimpleExchangeRegistry register(ExchangePort port) {
        Objects.requireNonNull(port, "port");
        map.put(port.id(), port);
        return this;
    }

    @Override
    public ExchangePort get(ExchangeId exchangeId) {
        ExchangePort port = map.get(exchangeId);
        if (port == null) {
            throw new IllegalStateException("Exchange adapter not registered: " + exchangeId);
        }
        return port;
    }
}
