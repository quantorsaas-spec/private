package com.quantor.application.guard;

import com.quantor.application.ports.SubscriptionPort;
import com.quantor.domain.trading.StopReason;
import com.quantor.domain.trading.StopReasonCode;
import com.quantor.domain.trading.UserId;

import java.util.Objects;

/**
 * Single point of truth for paywall on trading start.
 */
public final class TradePermissionGuard {

    private final SubscriptionPort subscriptionPort;

    public TradePermissionGuard(SubscriptionPort subscriptionPort) {
        this.subscriptionPort = Objects.requireNonNull(subscriptionPort, "subscriptionPort");
    }

    public void assertCanStart(UserId userId) {
        if (!subscriptionPort.canTrade(userId)) {
            throw new SubscriptionRequiredException("Subscription required to start live trading",
                    StopReason.of(StopReasonCode.SUBSCRIPTION_REQUIRED, "Subscription required"));
        }
    }
}
