package com.quantor.api.saas;

import com.quantor.saas.domain.model.PlanCode;
import com.quantor.saas.domain.model.PlanLimits;
import com.quantor.saas.domain.model.SubscriptionStatus;
import com.quantor.saas.infrastructure.entitlement.EntitlementEntity;
import com.quantor.saas.infrastructure.entitlement.EntitlementRepository;
import com.quantor.saas.infrastructure.subscription.SubscriptionEntity;
import com.quantor.saas.infrastructure.subscription.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Reconciles DB entitlements for a user based on their latest subscription.
 * This is the "single source of truth" for plan limits enforcement.
 */
@Service
public class EntitlementsService {

  private final SubscriptionRepository subscriptions;
  private final EntitlementRepository entitlements;

  public EntitlementsService(SubscriptionRepository subscriptions, EntitlementRepository entitlements) {
    this.subscriptions = subscriptions;
    this.entitlements = entitlements;
  }

  public EntitlementEntity reconcileForUser(UUID userId) {
    SubscriptionEntity sub = subscriptions.findFirstByUserIdOrderByUpdatedAtDesc(userId).orElse(null);

    PlanCode plan = PlanCode.FREE;
    SubscriptionStatus status = SubscriptionStatus.EXPIRED;
    Instant periodEnd = null;

    if (sub != null) {
      plan = parsePlan(sub.getPlan());
      status = parseStatus(sub.getStatus());
      periodEnd = sub.getCurrentPeriodEndsAt();
    }

    // Effective access: ACTIVE/TRIAL or within paid period => paid plan; otherwise FREE
    boolean paid = (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIAL)
        || (periodEnd != null && periodEnd.isAfter(Instant.now()));

    PlanLimits limits = PlanCatalog.limits(paid ? plan : PlanCode.FREE);

    EntitlementEntity e = entitlements.findById(userId).orElseGet(() -> new EntitlementEntity(userId));
    e.setMaxBots(limits.maxBots());
    e.setMaxTradesPerDay(limits.maxTradesPerDay());
    e.setDailyLossLimitPct(limits.dailyLossLimitPct());
    e.setTelegramControl(limits.telegramControl());
    e.setAdvancedStrategies(limits.advancedStrategies());
    e.setPaperTradingAllowed(limits.paperTradingAllowed());
    e.setLiveTradingAllowed(limits.liveTradingAllowed());
    e.setUpdatedAt(Instant.now());
    return entitlements.save(e);
  }

  private static PlanCode parsePlan(String v) {
    if (v == null) return PlanCode.FREE;
    try { return PlanCode.valueOf(v.trim().toUpperCase(Locale.ROOT)); }
    catch (Exception e) { return PlanCode.FREE; }
  }

  private static SubscriptionStatus parseStatus(String v) {
    if (v == null) return SubscriptionStatus.EXPIRED;
    String s = v.trim().toLowerCase(Locale.ROOT);
    if (s.contains("trial")) return SubscriptionStatus.TRIAL;
    if (s.contains("active")) return SubscriptionStatus.ACTIVE;
    if (s.contains("pause")) return SubscriptionStatus.PAUSED;
    if (s.contains("cancel")) return SubscriptionStatus.CANCELED;
    return SubscriptionStatus.EXPIRED;
  }
}
