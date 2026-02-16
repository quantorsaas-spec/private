package com.quantor.application.ports;

import com.quantor.domain.trading.UserId;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * SaaS billing/subscription boundary.
 *
 * Core rule (fail-closed):
 * - If we can't prove entitlement -> treat as BLOCKED (no trading).
 *
 * P0 usage:
 * - Must be checked in core BEFORE starting or resuming LIVE trading.
 * - UI (Telegram/CLI/API) must not be the source of truth.
 */
public interface SubscriptionPort {

    /**
     * High-level entitlement status understood by the trading core.
     */
    enum Status {
        FREE,
        PAID,
        GRACE,
        BLOCKED
    }

    /**
     * Latest subscription snapshot for the user (as seen by billing system).
     * This is the raw source for entitlement decisions.
     */
    Optional<SubscriptionSnapshot> findLatest(UserId userId);

    /**
     * Compatibility method used by core as a simple gate.
     * Default implementation derives status from the latest snapshot (fail-closed).
     *
     * Implementation note:
     * - If you override this, keep fail-closed semantics.
     */
    default Status status(UserId userId) {
        try {
            return findLatest(userId).map(SubscriptionPort::deriveStatus).orElse(Status.BLOCKED);
        } catch (Exception e) {
            // fail-closed on any storage/integration failure
            return Status.BLOCKED;
        }
    }

    /**
     * Convenience gate used by core.
     */
    default boolean canTrade(UserId userId) {
        Status s = status(userId);
        return s == Status.PAID || s == Status.GRACE;
    }

    /**
     * Snapshot model (billing-facing).
     * Plan/status values come from billing adapter (e.g., Lemon Squeezy).
     */
    record SubscriptionSnapshot(
            UUID userId,
            String plan,                 // "FREE" | "PRO" | "PRO_PLUS" | "ENTERPRISE"
            String status,               // "ACTIVE" | "TRIALING" | "PAST_DUE" | "CANCELLED" | "EXPIRED" | ...
            boolean frozen,
            OffsetDateTime currentPeriodEndsAt
    ) {}

    /**
     * Derive core entitlement from billing snapshot.
     * This is intentionally conservative.
     */
    static Status deriveStatus(SubscriptionSnapshot s) {
        if (s == null) return Status.BLOCKED;

        if (s.frozen()) return Status.BLOCKED;

        // expired period => not allowed
        OffsetDateTime now = OffsetDateTime.now();
        if (s.currentPeriodEndsAt() != null && !s.currentPeriodEndsAt().isAfter(now)) {
            return Status.FREE;
        }

        String plan = norm(s.plan());
        String st = norm(s.status());

        // paid plans only
        boolean paidPlan = !(plan.isBlank() || plan.equals("FREE"));

        if (!paidPlan) return Status.FREE;

        // allow only active/trialing
        if (st.equals("ACTIVE") || st.equals("TRIALING")) return Status.PAID;

        // grace window policy:
        // If you want a grace period on payment retries, map PAST_DUE -> GRACE here.
        // MVP recommendation: keep it BLOCKED to protect money and reduce risk.
        if (st.equals("PAST_DUE")) return Status.BLOCKED;

        // cancelled/expired/unknown => deny
        return Status.BLOCKED;
    }

    private static String norm(String v) {
        return v == null ? "" : v.trim().toUpperCase(Locale.ROOT);
    }
}
