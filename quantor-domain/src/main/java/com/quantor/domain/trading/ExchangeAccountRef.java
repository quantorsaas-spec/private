package com.quantor.domain.trading;

import java.util.Objects;

public record ExchangeAccountRef(String exchangeId, String accountId) {
    public ExchangeAccountRef {
        Objects.requireNonNull(exchangeId, "exchangeId");
        Objects.requireNonNull(accountId, "accountId");
        if (exchangeId.isBlank() || accountId.isBlank()) {
            throw new IllegalArgumentException("ExchangeAccountRef contains blank values");
        }
    }
}
