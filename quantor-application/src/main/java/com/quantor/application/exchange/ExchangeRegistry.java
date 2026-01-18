package com.quantor.application.exchange;

/**
 * Simple resolver for exchange adapters.
 *
 * Infrastructure provides an implementation (map, DI container, etc.).
 */
public interface ExchangeRegistry {
    ExchangePort get(ExchangeId exchangeId);
}
