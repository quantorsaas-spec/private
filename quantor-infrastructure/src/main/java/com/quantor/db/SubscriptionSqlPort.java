package com.quantor.db;

import com.quantor.application.ports.SubscriptionPort;
import com.quantor.domain.trading.UserId;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

/**
 * DB-backed SubscriptionPort (P0).
 *
 * Source of truth: user_subscriptions table updated by Lemon Squeezy webhooks.
 */
public final class SubscriptionSqlPort implements SubscriptionPort {

    @Override
    public Status status(UserId userId) {
        if (userId == null) return Status.FREE;

        Database.ensureDataDir();
        Database.initSchema();

        String sql = "SELECT status FROM user_subscriptions WHERE user_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId.value());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Status.FREE;
                String s = rs.getString("status");
                return mapStatus(s);
            }
        } catch (Exception e) {
            throw new RuntimeException("Subscription status lookup failed for user: " + userId.value(), e);
        }
    }

    public void upsert(String userId,
                       String subscriptionId,
                       Integer variantId,
                       String lemonStatus,
                       String renewsAt,
                       String endsAt) {
        Database.ensureDataDir();
        Database.initSchema();

        String sql = """
            INSERT INTO user_subscriptions(user_id, subscription_id, variant_id, status, renews_at, ends_at, updated_at)
            VALUES(?,?,?,?,?,?,?)
            ON CONFLICT(user_id) DO UPDATE SET
              subscription_id=excluded.subscription_id,
              variant_id=excluded.variant_id,
              status=excluded.status,
              renews_at=excluded.renews_at,
              ends_at=excluded.ends_at,
              updated_at=excluded.updated_at
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, userId);
            ps.setString(2, subscriptionId);
            if (variantId == null) ps.setObject(3, null);
            else ps.setInt(3, variantId);
            ps.setString(4, lemonStatus == null ? "unknown" : lemonStatus);
            ps.setString(5, renewsAt);
            ps.setString(6, endsAt);
            ps.setString(7, Instant.now().toString());

            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Subscription upsert failed for user: " + userId, e);
        }
    }

    private static Status mapStatus(String lemonStatus) {
        if (lemonStatus == null) return Status.FREE;
        String s = lemonStatus.trim().toLowerCase();

        // Lemon Squeezy docs: allow access in these states; expired = no access.
        // We'll treat "unpaid" and "expired" as BLOCKED.
        return switch (s) {
            case "active" -> Status.PAID;
            case "on_trial" -> Status.GRACE;
            case "paused" -> Status.GRACE;
            case "past_due" -> Status.GRACE;
            case "cancelled" -> Status.GRACE; // valid until ends_at
            case "unpaid", "expired" -> Status.BLOCKED;
            default -> Status.FREE;
        };
    }
}
