package com.quantor.application.ports;

import com.quantor.domain.trading.UserId;

/**
 * SaaS billing/subscription boundary.
 * P0: used as a hard gate before any LIVE trading start.
 */
public interface SubscriptionPort {

    enum Status {
        FREE,
        PAID,
        GRACE,
        BLOCKED
    }

    Status status(UserId userId);

    default boolean canTrade(UserId userId) {
        Status s = status(userId);
        return s == Status.PAID || s == Status.GRACE;
    }
}
