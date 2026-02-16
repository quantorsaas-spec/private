// File: quantor-application/src/main/java/com/quantor/application/guard/TradePermissionGuard.java
package com.quantor.application.guard;

import com.quantor.application.ports.SubscriptionPort;
import com.quantor.domain.trading.StopReason;
import com.quantor.domain.trading.StopReasonCode;
import com.quantor.domain.trading.UserId;

import java.util.Objects;

/**
 * Single point of truth for subscription paywall on trading start.
 *
 * Rules:
 * - Fail-closed: if subscription cannot prove entitlement -> deny.
 * - Used at API/orchestrator level (before SessionService.start()).
 * - SessionService and TradingPipeline also enforce billing (defense-in-depth).
 */
public final class TradePermissionGuard {

    private final SubscriptionPort subscriptionPort;

    public TradePermissionGuard(SubscriptionPort subscriptionPort) {
        this.subscriptionPort = Objects.requireNonNull(subscriptionPort, "subscriptionPort");
    }

    /**
     * Asserts that user is allowed to start trading.
     *
     * @throws SubscriptionRequiredException if trading is not allowed
     */
    public void assertCanStart(UserId userId) {
        if (userId == null) {
            throw denied();
        }

        // SubscriptionPort.canTrade() is expected to be fail-closed
        if (!subscriptionPort.canTrade(userId)) {
            throw denied();
        }
    }

    private SubscriptionRequiredException denied() {
        return new SubscriptionRequiredException(
                "Subscription required to start live trading",
                StopReason.of(
                        StopReasonCode.SUBSCRIPTION_REQUIRED,
                        "Subscription required"
                )
        );
    }
}
