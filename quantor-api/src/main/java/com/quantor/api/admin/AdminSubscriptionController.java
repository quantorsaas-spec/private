package com.quantor.api.admin;

import com.quantor.saas.infrastructure.subscription.SubscriptionEntity;
import com.quantor.saas.infrastructure.subscription.SubscriptionRepository;
import com.quantor.saas.infrastructure.user.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/subscriptions")
public class AdminSubscriptionController {

  private final SubscriptionRepository subscriptions;
  private final UserRepository users;
  private final AdminAuditService audit;

  public AdminSubscriptionController(SubscriptionRepository subscriptions, UserRepository users, AdminAuditService audit) {
    this.subscriptions = subscriptions;
    this.users = users;
    this.audit = audit;
  }

  @GetMapping("/{userId}")
  public Map<String, Object> getForUser(@PathVariable UUID userId) {
    users.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
    var sub = subscriptions.findFirstByUserIdOrderByUpdatedAtDesc(userId).orElse(null);
    if (sub == null) {
      return Map.of(
          "userId", userId,
          "exists", false
      );
    }
    return Map.of(
        "userId", userId,
        "exists", true,
        "subscriptionId", sub.getId(),
        "plan", sub.getPlan(),
        "status", sub.getStatus(),
        "currentPeriodEndsAt", sub.getCurrentPeriodEndsAt() == null ? null : sub.getCurrentPeriodEndsAt().toString(),
        "externalSubscriptionId", sub.getExternalSubscriptionId(),
        "frozen", sub.isFrozen(),
        "frozenAt", sub.getFrozenAt() == null ? null : sub.getFrozenAt().toString(),
        "updatedAt", sub.getUpdatedAt() == null ? null : sub.getUpdatedAt().toString()
    );
  }

  @PostMapping("/{userId}/freeze")
  public Map<String, Object> freeze(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
    UUID adminId = jwt == null ? null : UUID.fromString(jwt.getSubject());
    users.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

    SubscriptionEntity sub = subscriptions.findFirstByUserIdOrderByUpdatedAtDesc(userId).orElseGet(() ->
        new SubscriptionEntity(
            UUID.randomUUID(),
            userId,
            "FREE",
            "ADMIN_FROZEN",
            null,
            null,
            Instant.now()
        )
    );

    sub.freeze(Instant.now());
    sub.setUpdatedAt(Instant.now());
    subscriptions.save(sub);

    if (adminId != null) audit.logAdmin(adminId, "SUBSCRIPTION_FREEZE", "USER", userId.toString());

    return Map.of("status", "ok", "userId", userId, "frozen", true);
  }

  @PostMapping("/{userId}/unfreeze")
  public Map<String, Object> unfreeze(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
    UUID adminId = jwt == null ? null : UUID.fromString(jwt.getSubject());
    users.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

    SubscriptionEntity sub = subscriptions.findFirstByUserIdOrderByUpdatedAtDesc(userId)
        .orElseThrow(() -> new IllegalArgumentException("Subscription not found for user"));

    sub.unfreeze();
    sub.setUpdatedAt(Instant.now());
    subscriptions.save(sub);

    if (adminId != null) audit.logAdmin(adminId, "SUBSCRIPTION_UNFREEZE", "USER", userId.toString());

    return Map.of("status", "ok", "userId", userId, "frozen", false);
  }
}
