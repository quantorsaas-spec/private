package com.quantor.domain.trading;

import java.util.Objects;
import java.util.UUID;

public record SessionId(String value) {
    public SessionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("SessionId is blank");
    }

    public static SessionId newId() {
        return new SessionId(UUID.randomUUID().toString());
    }
}
