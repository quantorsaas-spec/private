package com.quantor.api.wiring;

import com.quantor.application.ports.SubscriptionPort;
import com.quantor.domain.trading.UserId;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Postgres-backed implementation of {@link SubscriptionPort}.
 *
 * IMPORTANT: the public API still uses {@link UserId} as a string.
 * In PROD we treat this string as a UUID that matches users.id / subscriptions.user_id.
 */
public final class PostgresSubscriptionPort implements SubscriptionPort {

    private final JdbcTemplate jdbc;

    public PostgresSubscriptionPort(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SubscriptionPort.Status status(UserId userId) {
        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId.value());
        } catch (Exception e) {
            // Developer convenience: allow querying by email.
            // If it doesn't match any user, treat as FREE.
            try {
                userUuid = jdbc.queryForObject(
                        "SELECT id FROM users WHERE lower(email) = lower(?) LIMIT 1",
                        (rs, rowNum) -> (UUID) rs.getObject(1),
                        userId.value()
                );
                if (userUuid == null) {
                    return SubscriptionPort.Status.FREE;
                }
            } catch (EmptyResultDataAccessException notFound) {
                return SubscriptionPort.Status.FREE;
            }
        }

        Optional<Row> row = findLatestSubscription(userUuid);
        if (row.isEmpty()) {
            return SubscriptionPort.Status.FREE;
        }

        Row r = row.get();
        if (r.frozen) {
            return SubscriptionPort.Status.BLOCKED;
        }

        // Normalize status string from DB.
        String s = (r.status == null ? "" : r.status.trim().toLowerCase());
        Instant now = Instant.now();
        Instant endsAt = r.currentPeriodEndsAt;

        if ("active".equals(s)) {
            return SubscriptionPort.Status.PAID;
        }

        // Grace period behavior for non-active statuses.
        if (endsAt != null && endsAt.isAfter(now)) {
            return SubscriptionPort.Status.GRACE;
        }

        return SubscriptionPort.Status.FREE;
    }

    private Optional<Row> findLatestSubscription(UUID userUuid) {
        try {
            Row row = jdbc.queryForObject(
                    "select status, current_period_ends_at, frozen "
                            + "from subscriptions "
                            + "where user_id = ? "
                            + "order by updated_at desc nulls last "
                            + "limit 1",
                    (ResultSet rs, int rowNum) -> {
                        // Be defensive: depending on driver/version, timestamptz may map more reliably to OffsetDateTime.
                        java.time.OffsetDateTime odt = rs.getObject("current_period_ends_at", java.time.OffsetDateTime.class);
                        Instant ends = (odt == null ? null : odt.toInstant());
                        return new Row(
                                rs.getString("status"),
                                ends,
                                rs.getBoolean("frozen"));
                    },
                    userUuid);
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private record Row(String status, Instant currentPeriodEndsAt, boolean frozen) {}
}
