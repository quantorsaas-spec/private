package com.quantor.domain.trading;

import java.util.Objects;

public record StopReason(StopReasonCode code, String message) {
    public StopReason {
        Objects.requireNonNull(code, "code");
        if (message == null || message.isBlank()) {
            message = code.name();
        }
    }

    public static StopReason of(StopReasonCode code) {
        return new StopReason(code, code.name());
    }

    public static StopReason of(StopReasonCode code, String message) {
        return new StopReason(code, message);
    }
}
