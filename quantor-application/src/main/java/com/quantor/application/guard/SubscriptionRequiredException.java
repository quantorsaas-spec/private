package com.quantor.application.guard;

import com.quantor.domain.DomainException;
import com.quantor.domain.trading.StopReason;

import java.util.Objects;

public final class SubscriptionRequiredException extends DomainException {

    private final StopReason stopReason;

    public SubscriptionRequiredException(String message, StopReason stopReason) {
        super(message);
        this.stopReason = Objects.requireNonNull(stopReason, "stopReason");
    }

    public StopReason stopReason() {
        return stopReason;
    }
}
