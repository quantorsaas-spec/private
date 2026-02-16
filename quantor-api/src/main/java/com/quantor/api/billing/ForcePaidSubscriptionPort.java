package com.quantor.api.billing;

import com.quantor.application.ports.SubscriptionPort;
import com.quantor.domain.trading.UserId;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public final class ForcePaidSubscriptionPort implements SubscriptionPort {

    @Override
    public Optional<SubscriptionSnapshot> findLatest(UserId userId) {
        UUID uid;
        try {
            uid = UUID.fromString(userId.value());
        } catch (Exception e) {
            // fail-closed snapshot: missing/invalid userId -> BLOCKED/FREE by deriveStatus rules
            return Optional.empty();
        }

        // treat as paid forever (local/dev only)
        return Optional.of(new SubscriptionSnapshot(
                uid,
                "PRO",
                "ACTIVE",
                false,
                OffsetDateTime.now().plusYears(10)
        ));
    }
}
