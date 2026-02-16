package com.quantor.db;

import com.quantor.application.ports.SubscriptionPort;
import com.quantor.domain.trading.UserId;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Postgres-backed SubscriptionPort (table: subscriptions).
 *
 * Core rule: FAIL-CLOSED.
 * - Any error or missing data => Optional.empty()
 * - SubscriptionPort.status() default => BLOCKED
 *
 * P0:
 * - NO dev bypass here.
 */
public final class SubscriptionSqlPort implements SubscriptionPort {

    private final DataSource dataSource;

    public SubscriptionSqlPort(DataSource dataSource) {
        this.dataSource = require(dataSource, "dataSource");
    }

    @Override
    public Optional<SubscriptionPort.SubscriptionSnapshot> findLatest(UserId userId) {
        if (userId == null || userId.value() == null || userId.value().isBlank()) {
            return Optional.empty();
        }

        UUID uid = parseUuidOrNull(userId.value());
        if (uid == null) return Optional.empty();

        final String sql = """
                SELECT user_id, plan, status, frozen, current_period_ends_at
                FROM subscriptions
                WHERE user_id = ?
                ORDER BY updated_at DESC
                LIMIT 1
                """;

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setObject(1, uid);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                UUID u = (UUID) rs.getObject("user_id");
                String plan = rs.getString("plan");
                String status = rs.getString("status");
                boolean frozen = rs.getBoolean("frozen");

                OffsetDateTime endsAt = null;
                Object ts = rs.getObject("current_period_ends_at");
                if (ts instanceof OffsetDateTime odt) {
                    endsAt = odt;
                } else if (ts instanceof java.sql.Timestamp tss) {
                    endsAt = tss.toInstant().atOffset(ZoneOffset.UTC);
                }

                return Optional.of(new SubscriptionPort.SubscriptionSnapshot(u, plan, status, frozen, endsAt));
            }

        } catch (Exception e) {
            return Optional.empty(); // FAIL-CLOSED
        }
    }

    private static UUID parseUuidOrNull(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private static <T> T require(T v, String name) {
        if (v == null) throw new IllegalArgumentException(name + " is null");
        return v;
    }
}
