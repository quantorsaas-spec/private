package com.quantor.infrastructure.subscription;

import com.quantor.application.ports.SubscriptionPort;
import com.quantor.domain.trading.UserId;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * MVP subscription adapter.
 *
 * Mode:
 * - If billing.forcePaid=true -> PAID
 * - Else -> BLOCKED
 *
 * Fail-closed by default.
 */
public class DevSubscriptionAdapter implements SubscriptionPort {

    private final boolean forcePaid;

    public DevSubscriptionAdapter(boolean forcePaid) {
        this.forcePaid = forcePaid;
    }

    @Override
    public Optional<SubscriptionSnapshot> findLatest(UserId userId) {
        if (!forcePaid) {
            return Optional.empty(); // BLOCKED
        }

        return Optional.of(
                new SubscriptionSnapshot(
                        UUID.fromString(userId.value()),
                        "PRO",
                        "ACTIVE",
                        false,
                        OffsetDateTime.now().plusYears(1)
                )
        );
    }
}
