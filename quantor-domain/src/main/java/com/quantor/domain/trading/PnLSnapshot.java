package com.quantor.domain.trading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record PnLSnapshot(BigDecimal realized, BigDecimal unrealized, Instant updatedAt) {
    public PnLSnapshot {
        Objects.requireNonNull(realized, "realized");
        Objects.requireNonNull(unrealized, "unrealized");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static PnLSnapshot zero() {
        return new PnLSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, Instant.now());
    }
}
