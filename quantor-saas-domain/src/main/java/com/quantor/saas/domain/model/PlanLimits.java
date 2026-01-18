package com.quantor.saas.domain.model;

/**
 * Limits per plan.
 *
 * Notes:
 * - This record is referenced from API (entitlements) and from the plan catalog.
 * - Keep the surface stable; if a limit is not enforced yet, it should still be present
 *   so the API can expose it consistently.
 */
public record PlanLimits(
        int maxBots,
        int maxSymbols,
        int maxStrategies,
        int maxSessions,
        int maxLookback,
        int maxTradesPerDay,
        double dailyLossLimitPct,
        boolean telegramControl,
        boolean advancedStrategies,
        boolean paperTradingAllowed,
        boolean liveTradingAllowed
) {
}
