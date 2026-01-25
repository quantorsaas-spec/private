package com.quantor.api.admin;

import com.quantor.saas.infrastructure.engine.BotInstanceRepository;
import com.quantor.saas.infrastructure.subscription.SubscriptionRepository;
import com.quantor.saas.infrastructure.user.UserEntity;
import com.quantor.saas.infrastructure.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

  private final UserRepository users;
  private final SubscriptionRepository subscriptions;
  private final BotInstanceRepository instances;

  public AdminUserController(UserRepository users, SubscriptionRepository subscriptions, BotInstanceRepository instances) {
    this.users = users;
    this.subscriptions = subscriptions;
    this.instances = instances;
  }

  public record UserRow(
      UUID id,
      String email,
      String role,
      boolean enabled,
      String createdAt,
      String plan,
      String subscriptionStatus,
      boolean subscriptionFrozen,
      long activeBots
  ) {}

  @GetMapping
  public List<UserRow> list(@RequestParam(defaultValue = "50") int limit) {
    int size = Math.max(1, Math.min(200, limit));
    var page = users.findAll(PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    List<UserRow> res = new ArrayList<>();
    for (UserEntity u : page.getContent()) {
      var sub = subscriptions.findFirstByUserIdOrderByUpdatedAtDesc(u.getId()).orElse(null);
      boolean frozen = sub != null && sub.isFrozen();
      String plan = sub == null ? "FREE" : String.valueOf(sub.getPlan());
      String st = sub == null ? "NONE" : String.valueOf(sub.getStatus());
      long active = instances.countByUserIdAndStatusIn(u.getId(), List.of("RUNNING","PAUSED","PENDING"));
      res.add(new UserRow(
          u.getId(),
          u.getEmail(),
          u.getRole(),
          u.isEnabled(),
          u.getCreatedAt() == null ? null : u.getCreatedAt().toString(),
          plan,
          st,
          frozen,
          active
      ));
    }
    return res;
  }

  @GetMapping("/{id}")
  public UserRow get(@PathVariable UUID id) {
    UserEntity u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
    var sub = subscriptions.findFirstByUserIdOrderByUpdatedAtDesc(u.getId()).orElse(null);
    boolean frozen = sub != null && sub.isFrozen();
    String plan = sub == null ? "FREE" : String.valueOf(sub.getPlan());
    String st = sub == null ? "NONE" : String.valueOf(sub.getStatus());
    long active = instances.countByUserIdAndStatusIn(u.getId(), List.of("RUNNING","PAUSED","PENDING"));
    return new UserRow(
        u.getId(),
        u.getEmail(),
        u.getRole(),
        u.isEnabled(),
        u.getCreatedAt() == null ? null : u.getCreatedAt().toString(),
        plan,
        st,
        frozen,
        active
    );
  }
}
