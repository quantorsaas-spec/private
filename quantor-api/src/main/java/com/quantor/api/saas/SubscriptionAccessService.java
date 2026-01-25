package com.quantor.api.saas;

import com.quantor.saas.domain.model.PlanCode;
import com.quantor.saas.domain.model.PlanLimits;
import com.quantor.saas.domain.model.SubscriptionStatus;
import com.quantor.saas.infrastructure.engine.BotInstanceRepository;
import com.quantor.saas.infrastructure.subscription.SubscriptionEntity;
import com.quantor.saas.infrastructure.subscription.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SubscriptionAccessService {

  private final SubscriptionRepository subscriptions;
  private final BotInstanceRepository instances;

  public SubscriptionAccessService(SubscriptionRepository subscriptions, BotInstanceRepository instances) {
    this.subscriptions = subscriptions;
    this.instances = instances;
  }

  public PlanCode effectivePlan(String userId) {
    UUID uid = UUID.fromString(userId);
    return subscriptions.findFirstByUserIdOrderByUpdatedAtDesc(uid)
        .map(this::toPlanIfActiveOrFree)
        .orElse(PlanCode.FREE);
  }

  private PlanCode toPlanIfActiveOrFree(SubscriptionEntity s) {
    SubscriptionStatus st = parseStatus(s.getStatus());
    // treat TRIAL and ACTIVE as paid for access
    if (st == SubscriptionStatus.ACTIVE || st == SubscriptionStatus.TRIAL) {
      return parsePlan(s.getPlan());
    }
    // if period still valid, keep access (grace)
    if (s.getCurrentPeriodEndsAt() != null && s.getCurrentPeriodEndsAt().isAfter(Instant.now())) {
      return parsePlan(s.getPlan());
    }
    return PlanCode.FREE;
  }

  public PlanLimits limitsForUser(String userId) {
    return PlanCatalog.limits(effectivePlan(userId));
  }

  public void assertCanStart(String userId) {
  UUID uid = UUID.fromString(userId);
  SubscriptionEntity sub = subscriptions.findFirstByUserIdOrderByUpdatedAtDesc(uid).orElse(null);
  if (sub != null && sub.isFrozen()) {
    throw new IllegalArgumentException("Account is frozen by admin");
  }

  PlanLimits limits = PlanCatalog.limits(effectivePlan(userId));
  long active = instances.countByUserIdAndStatusIn(
      uid,
      List.of("RUNNING", "PAUSED", "PENDING")
  );
  if (active >= limits.maxBots()) {
    throw new IllegalArgumentException("Plan limit reached: maxBots=" + limits.maxBots());
  }
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
