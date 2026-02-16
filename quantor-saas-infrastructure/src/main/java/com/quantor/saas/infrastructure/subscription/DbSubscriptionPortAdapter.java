// File: quantor-saas-infrastructure/src/main/java/com/quantor/saas/infrastructure/subscription/DbSubscriptionPortAdapter.java
package com.quantor.saas.infrastructure.subscription;

import com.quantor.application.ports.SubscriptionPort;
import com.quantor.domain.trading.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter: reads subscription row from SaaS DB and exposes it to trading core via SubscriptionPort.
 *
 * Fail-closed policy:
 * - if userId cannot be mapped to UUID -> return empty
 * - if subscription missing -> return empty
 */
@Component
public class DbSubscriptionPortAdapter implements SubscriptionPort {

    private final SubscriptionRepository subscriptions;

    public DbSubscriptionPortAdapter(SubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public Optional<SubscriptionSnapshot> findLatest(UserId userId) {
        Optional<UUID> uid = toUuid(userId);
        if (uid.isEmpty()) return Optional.empty();

        // MVP: one subscription row per user. If you later support multiple, order by updated_at desc.
        return subscriptions.findTopByUserIdOrderByUpdatedAtDesc(uid.get())
                .map(this::toSnapshot);
    }

    private SubscriptionSnapshot toSnapshot(SubscriptionEntity e) {
        return new SubscriptionSnapshot(
                e.getUserId(),
                e.getPlan(),
                e.getStatus(),
                e.isFrozen(),
                toOffsetDateTime(e.getCurrentPeriodEndsAt())
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant v) {
        if (v == null) return null;
        return OffsetDateTime.ofInstant(v, ZoneOffset.UTC);
    }

    /**
     * Conservative UUID extraction.
     * We assume UserId.toString() is a UUID string (common in your config: userId=<uuid>).
     */
    private static Optional<UUID> toUuid(UserId userId) {
        if (userId == null) return Optional.empty();
        String s = userId.toString();
        if (s == null) return Optional.empty();
        s = s.trim();
        if (s.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(s));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
