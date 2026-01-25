package com.quantor.saas.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Subscription state synced from billing provider (Lemon Squeezy).
 */
public record Subscription(
        UUID id,
        UUID userId,
        PlanCode plan,
        SubscriptionStatus status,
        Instant currentPeriodEndsAt,
        String externalSubscriptionId,
        Instant updatedAt
) {
}
