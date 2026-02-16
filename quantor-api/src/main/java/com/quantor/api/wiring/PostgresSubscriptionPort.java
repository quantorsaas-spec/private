// File: quantor-api/src/main/java/com/quantor/api/wiring/PostgresSubscriptionPort.java
package com.quantor.api.wiring;

import com.quantor.application.ports.SubscriptionPort;
import com.quantor.domain.trading.UserId;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Postgres-backed implementation of {@link SubscriptionPort}.
 *
 * IMPORTANT:
 * - In PROD we treat UserId.value() as a UUID that matches users.id / subscriptions.user_id.
 * - For developer convenience we also allow passing email; it will be resolved to users.id.
 *
 * SECURITY (fail-closed):
 * - If user cannot be resolved to a real UUID -> return Optional.empty() (core will treat as BLOCKED).
 */
public final class PostgresSubscriptionPort implements SubscriptionPort {

    private final JdbcTemplate jdbc;

    public PostgresSubscriptionPort(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SubscriptionSnapshot> findLatest(UserId userId) {
        UUID userUuid = resolveUserUuid(userId).orElse(null);
        if (userUuid == null) {
            // fail-closed: cannot resolve user -> no entitlement proven
            return Optional.empty();
        }
        return findLatestSubscription(userUuid);
    }

    /**
     * Resolve UserId to users.id (UUID).
     * Accepts:
     * - UUID string
     * - email (dev convenience)
     */
    private Optional<UUID> resolveUserUuid(UserId userId) {
        if (userId == null || userId.value() == null || userId.value().isBlank()) {
            return Optional.empty();
        }

        String raw = userId.value().trim();

        // 1) UUID direct
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (Exception ignore) {
            // continue to email resolution
        }

        // 2) email -> users.id
        try {
            UUID uuid = jdbc.queryForObject(
                    "SELECT id FROM users WHERE lower(email) = lower(?) LIMIT 1",
                    (rs, rowNum) -> (UUID) rs.getObject(1),
                    raw
            );
            return Optional.ofNullable(uuid);
        } catch (EmptyResultDataAccessException notFound) {
            return Optional.empty();
        }
    }

    private Optional<SubscriptionSnapshot> findLatestSubscription(UUID userUuid) {
        try {
            SubscriptionSnapshot snap = jdbc.queryForObject(
                    "select user_id, plan, status, current_period_ends_at, frozen " +
                            "from subscriptions " +
                            "where user_id = ? " +
                            "order by updated_at desc nulls last " +
                            "limit 1",
                    (ResultSet rs, int rowNum) -> {
                        UUID uid = (UUID) rs.getObject("user_id");
                        String plan = rs.getString("plan");
                        String status = rs.getString("status");
                        OffsetDateTime endsAt = rs.getObject("current_period_ends_at", OffsetDateTime.class);
                        boolean frozen = rs.getBoolean("frozen");
                        return new SubscriptionSnapshot(uid, plan, status, frozen, endsAt);
                    },
                    userUuid
            );
            return Optional.ofNullable(snap);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
