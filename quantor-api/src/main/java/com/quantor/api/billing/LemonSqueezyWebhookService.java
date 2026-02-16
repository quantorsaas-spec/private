// File: quantor-api/src/main/java/com/quantor/api/billing/LemonSqueezyWebhookService.java
package com.quantor.api.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantor.saas.domain.model.PlanCode;
import com.quantor.saas.domain.model.SubscriptionStatus;
import com.quantor.saas.infrastructure.subscription.SubscriptionEntity;
import com.quantor.saas.infrastructure.subscription.SubscriptionRepository;
import com.quantor.saas.infrastructure.user.UserEntity;
import com.quantor.saas.infrastructure.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Syncs Lemon Squeezy webhook payloads into our SaaS tables.
 *
 * Matching a user:
 * - Prefer meta.custom_data.user_id (UUID string)
 * - Else try email fields in payload and match by users.email
 *
 * Plan mapping:
 * - Prefer configured mapping by variant_id
 * - Else fallback by variant_name keywords
 *
 * Fail-closed policy:
 * - If we cannot map confidently -> FREE
 * - If status not ACTIVE/TRIAL -> FREE
 *
 * Frozen policy:
 * - PAUSED/CANCELED/EXPIRED/PAST_DUE -> freeze
 * - ACTIVE/TRIAL -> unfreeze
 */
@Service
public class LemonSqueezyWebhookService {

  private final ObjectMapper mapper;
  private final UserRepository users;
  private final SubscriptionRepository subscriptions;

  private final String planProVariantId;
  private final String planProPlusVariantId;

  public LemonSqueezyWebhookService(
      ObjectMapper mapper,
      UserRepository users,
      SubscriptionRepository subscriptions,
      @Value("${quantor.lemonsqueezy.variantId.pro:}") String planProVariantId,
      @Value("${quantor.lemonsqueezy.variantId.proPlus:}") String planProPlusVariantId
  ) {
    this.mapper = mapper;
    this.users = users;
    this.subscriptions = subscriptions;
    this.planProVariantId = planProVariantId == null ? "" : planProVariantId.trim();
    this.planProPlusVariantId = planProPlusVariantId == null ? "" : planProPlusVariantId.trim();
  }

  public SyncResult handle(String eventName, String rawBody) {
    try {
      JsonNode root = mapper.readTree(rawBody);

      String externalId = readText(root, "data", "id");
      if (externalId == null || externalId.isBlank()) {
        return SyncResult.ignored("missing_external_id");
      }

      Optional<UUID> userId = extractUserId(root);
      Optional<String> email = userId.isEmpty() ? extractEmail(root) : Optional.empty();

      Optional<UserEntity> user = userId
          .flatMap(users::findById)
          .or(() -> email.flatMap(users::findByEmailIgnoreCase));

      if (user.isEmpty()) {
        return SyncResult.ignored("user_not_found");
      }

      SubscriptionStatus status = extractStatus(root, eventName);

      // Fail-closed: only ACTIVE/TRIAL can ever produce a paid plan
      PlanCode plan = extractPlanFailClosed(root, status);

      Instant periodEnd = extractPeriodEnd(root).orElse(null);

      SubscriptionEntity entity = subscriptions.findByExternalSubscriptionId(externalId)
          .orElseGet(() -> new SubscriptionEntity(
              UUID.randomUUID(),
              user.get().getId(),
              plan.name(),
              status.name(),
              periodEnd,
              externalId,
              Instant.now()
          ));

      entity.setPlan(plan.name());
      entity.setStatus(status.name());
      entity.setCurrentPeriodEndsAt(periodEnd);
      entity.setUpdatedAt(Instant.now());

      // Freeze/unfreeze is SOURCE OF TRUTH for the trading core
      if (shouldFreeze(status) || plan == PlanCode.FREE) {
        if (!entity.isFrozen()) entity.freeze(Instant.now());
      } else {
        if (entity.isFrozen()) entity.unfreeze();
      }

      subscriptions.save(entity);

      return SyncResult.ok(
          entity.getUserId().toString(),
          entity.getPlan(),
          entity.getStatus(),
          entity.getExternalSubscriptionId()
      );

    } catch (Exception e) {
      System.err.println("Lemon webhook sync failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
      return SyncResult.error("parse_or_sync_failed");
    }
  }

  private static boolean shouldFreeze(SubscriptionStatus status) {
    if (status == null) return true; // fail-closed
    return status == SubscriptionStatus.PAUSED
        || status == SubscriptionStatus.CANCELED
        || status == SubscriptionStatus.EXPIRED
        || status == SubscriptionStatus.PAST_DUE;
  }

  private Optional<UUID> extractUserId(JsonNode root) {
    String v = readText(root, "meta", "custom_data", "user_id");
    if (v == null) v = readText(root, "meta", "custom_data", "userId");
    if (v == null) return Optional.empty();
    try { return Optional.of(UUID.fromString(v.trim())); } catch (Exception e) { return Optional.empty(); }
  }

  private Optional<String> extractEmail(JsonNode root) {
    String[] paths = new String[] {
        "data.attributes.user_email",
        "data.attributes.customer_email",
        "data.attributes.email",
        "data.attributes.billing_email",
        "meta.custom_data.email"
    };
    for (String p : paths) {
      String v = readText(root, p);
      if (v != null && !v.isBlank()) return Optional.of(v.trim().toLowerCase(Locale.ROOT));
    }
    return Optional.empty();
  }

  /**
   * Fail-closed: if we can't confidently map to a paid plan, return FREE.
   */
  private PlanCode extractPlanFailClosed(JsonNode root, SubscriptionStatus status) {
    if (!(status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIAL)) {
      return PlanCode.FREE;
    }

    String variantId = readText(root, "data", "attributes", "variant_id");
    if (variantId == null) variantId = readText(root, "data", "attributes", "variantId");

    if (variantId != null) {
      String id = variantId.trim();
      if (!planProPlusVariantId.isBlank() && planProPlusVariantId.equals(id)) return PlanCode.PRO_PLUS;
      if (!planProVariantId.isBlank() && planProVariantId.equals(id)) return PlanCode.PRO;
    }

    String variantName = readText(root, "data", "attributes", "variant_name");
    if (variantName == null) variantName = readText(root, "data", "attributes", "variantName");
    if (variantName != null) {
      String n = variantName.toLowerCase(Locale.ROOT);
      if (n.contains("enterprise")) return PlanCode.ENTERPRISE;
      if (n.contains("plus") || n.contains("pro+")) return PlanCode.PRO_PLUS;
      if (n.contains("pro")) return PlanCode.PRO;
    }

    return PlanCode.FREE;
  }

  private SubscriptionStatus extractStatus(JsonNode root, String eventName) {
    String status = readText(root, "data", "attributes", "status");
    if (status == null) status = readText(root, "data", "attributes", "subscription_status");
    if (status == null && eventName != null) status = eventName;

    if (status == null) return SubscriptionStatus.PAST_DUE; // fail-closed-ish

    String s = status.toLowerCase(Locale.ROOT);

    if (s.contains("trial")) return SubscriptionStatus.TRIAL;
    if (s.contains("active")) return SubscriptionStatus.ACTIVE;

    if (s.contains("past_due") || s.contains("past-due") || s.contains("unpaid")) return SubscriptionStatus.PAST_DUE;
    if (s.contains("pause")) return SubscriptionStatus.PAUSED;
    if (s.contains("cancel") || s.contains("refunded")) return SubscriptionStatus.CANCELED;
    if (s.contains("expire")) return SubscriptionStatus.EXPIRED;

    if (s.contains("created") || s.contains("updated")) return SubscriptionStatus.ACTIVE;

    return SubscriptionStatus.PAST_DUE;
  }

  private Optional<Instant> extractPeriodEnd(JsonNode root) {
    String[] paths = new String[] {
        "data.attributes.renews_at",
        "data.attributes.ends_at",
        "data.attributes.current_period_end",
        "data.attributes.current_period_end_at",
        "data.attributes.trial_ends_at"
    };
    for (String p : paths) {
      String v = readText(root, p);
      if (v == null || v.isBlank()) continue;

      String s = v.trim();
      try { return Optional.of(Instant.parse(s)); } catch (Exception ignored) {}
      try { return Optional.of(OffsetDateTime.parse(s).toInstant()); } catch (Exception ignored) {}
      try {
        long epoch = Long.parseLong(s);
        if (epoch > 0) return Optional.of(Instant.ofEpochSecond(epoch));
      } catch (Exception ignored) {}
    }
    return Optional.empty();
  }

  private static String readText(JsonNode root, String dotPath) {
    String[] parts = dotPath.split("\\.");
    JsonNode n = root;
    for (String p : parts) {
      if (n == null) return null;
      n = n.get(p);
    }
    return (n != null && n.isValueNode()) ? n.asText() : null;
  }

  private static String readText(JsonNode root, String... path) {
    JsonNode n = root;
    for (String p : path) {
      if (n == null) return null;
      n = n.get(p);
    }
    return (n != null && n.isValueNode()) ? n.asText() : null;
  }

  public record SyncResult(String status, String reason, String userId, String plan, String subscriptionStatus, String externalId) {
    public static SyncResult ok(String userId, String plan, String subscriptionStatus, String externalId) {
      return new SyncResult("ok", null, userId, plan, subscriptionStatus, externalId);
    }
    public static SyncResult ignored(String reason) {
      return new SyncResult("ignored", reason, null, null, null, null);
    }
    public static SyncResult error(String reason) {
      return new SyncResult("error", reason, null, null, null, null);
    }
  }
}
