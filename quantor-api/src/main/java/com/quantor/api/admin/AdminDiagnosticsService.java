package com.quantor.api.admin;

import com.quantor.api.saas.PlanCatalog;
import com.quantor.api.saas.SubscriptionAccessService;
import com.quantor.saas.domain.model.PlanCode;
import com.quantor.saas.domain.model.PlanLimits;
import com.quantor.saas.infrastructure.engine.BotCommandEntity;
import com.quantor.saas.infrastructure.engine.BotCommandRepository;
import com.quantor.saas.infrastructure.engine.BotInstanceEntity;
import com.quantor.saas.infrastructure.engine.BotInstanceRepository;
import com.quantor.saas.infrastructure.security.UserApiKeyRepository;
import com.quantor.saas.infrastructure.subscription.SubscriptionEntity;
import com.quantor.saas.infrastructure.subscription.SubscriptionRepository;
import com.quantor.saas.infrastructure.user.UserEntity;
import com.quantor.saas.infrastructure.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class AdminDiagnosticsService {

  private final UserRepository users;
  private final SubscriptionRepository subscriptions;
  private final BotInstanceRepository instances;
  private final BotCommandRepository commands;
  private final UserApiKeyRepository apiKeys;
  private final SubscriptionAccessService access;
  private final JdbcTemplate jdbc;

  public AdminDiagnosticsService(
      UserRepository users,
      SubscriptionRepository subscriptions,
      BotInstanceRepository instances,
      BotCommandRepository commands,
      UserApiKeyRepository apiKeys,
      SubscriptionAccessService access,
      JdbcTemplate jdbc
  ) {
    this.users = users;
    this.subscriptions = subscriptions;
    this.instances = instances;
    this.commands = commands;
    this.apiKeys = apiKeys;
    this.access = access;
    this.jdbc = jdbc;
  }

  public StartEligibility diagnoseStartEligibility(UUID userId) {
    List<Reason> reasons = new ArrayList<>();
    Map<String, Object> details = new LinkedHashMap<>();

    Optional<UserEntity> uOpt = users.findById(userId);
    if (uOpt.isEmpty()) {
      reasons.add(new Reason("USER_NOT_FOUND", "User does not exist"));
      return new StartEligibility(false, reasons, details);
    }

    UserEntity u = uOpt.get();
    details.put("userId", userId.toString());
    details.put("email", u.getEmail());
    details.put("role", u.getRole());
    details.put("enabled", u.isEnabled());

    if (!u.isEnabled()) {
      reasons.add(new Reason("USER_DISABLED", "User account is disabled"));
    }

    // Exchange API keys (best-effort): required to start live trading.
    boolean hasBinanceKeys = apiKeys.existsByUserIdAndExchange(userId, "BINANCE");
    details.put("hasBinanceApiKeys", hasBinanceKeys);
    if (!hasBinanceKeys) {
      reasons.add(new Reason("NO_API_KEYS", "No Binance API keys configured for this user"));
    }

    SubscriptionEntity sub = subscriptions.findFirstByUserIdOrderByUpdatedAtDesc(userId).orElse(null);
    if (sub == null) {
      details.put("subscription", null);
    } else {
      Map<String, Object> subDetails = new LinkedHashMap<>();
      subDetails.put("plan", sub.getPlan());
      subDetails.put("status", sub.getStatus());
      subDetails.put("frozen", sub.isFrozen());
      subDetails.put("currentPeriodEndsAt", sub.getCurrentPeriodEndsAt());
      subDetails.put("externalSubscriptionId", sub.getExternalSubscriptionId());
      details.put("subscription", subDetails);

      if (sub.isFrozen()) {
        reasons.add(new Reason("SUBSCRIPTION_FROZEN", "Account is frozen by admin"));
      }
    }

    // Effective plan (treats expired/grace/free correctly)
    PlanCode plan = access.effectivePlan(userId.toString());
    PlanLimits limits = PlanCatalog.limits(plan);
    details.put("effectivePlan", plan.name());
    details.put("limits", Map.of(
        "maxBots", limits.maxBots(),
        "telegramControl", limits.telegramControl(),
        "advancedStrategies", limits.advancedStrategies()
    ));

    long activeBots = instances.countByUserIdAndStatusIn(userId, List.of("RUNNING", "PAUSED", "PENDING"));
    details.put("activeBots", activeBots);

    if (activeBots >= limits.maxBots()) {
      reasons.add(new Reason("LIMIT_MAX_BOTS", "Plan limit reached: maxBots=" + limits.maxBots()));
    }

    // Workers health (best-effort). Table is created by worker migrations.
    int aliveWorkers = countAliveWorkers(Duration.ofSeconds(30));
    details.put("workersAliveLast30s", aliveWorkers);

    if (aliveWorkers <= 0) {
      reasons.add(new Reason("NO_WORKERS", "No active workers heartbeat in the last 30 seconds"));
    }

    // Support context: if the user has a bot in ERROR, show the latest FAILED command (with traceparent).
    instances.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(userId, "ERROR").ifPresent(errBot -> {
      details.put("latestErrorBot", Map.of(
          "jobKey", errBot.getJobKey(),
          "strategyId", errBot.getStrategyId(),
          "symbol", errBot.getSymbol(),
          "interval", errBot.getInterval(),
          "updatedAt", errBot.getUpdatedAt()
      ));

      commands.findTopByBotInstanceIdAndStatusOrderByCreatedAtDesc(errBot.getId(), "FAILED")
          .ifPresent(failed -> details.put("latestFailedCommand", toFailedCommandDetails(failed)));
    });

    boolean eligible = reasons.isEmpty();
    return new StartEligibility(eligible, reasons, details);
  }

  public BotError diagnoseBotError(String jobKey) {
    Map<String, Object> details = new LinkedHashMap<>();
    Optional<BotInstanceEntity> botOpt = instances.findByJobKey(jobKey);
    if (botOpt.isEmpty()) {
      return new BotError(false, List.of(new Reason("BOT_NOT_FOUND", "Bot instance does not exist")), details);
    }

    BotInstanceEntity bot = botOpt.get();
    details.put("jobKey", bot.getJobKey());
    details.put("userId", bot.getUserId().toString());
    details.put("status", bot.getStatus());
    details.put("strategyId", bot.getStrategyId());
    details.put("symbol", bot.getSymbol());
    details.put("interval", bot.getInterval());
    details.put("updatedAt", bot.getUpdatedAt());
    details.put("leaseOwner", bot.getLeaseOwner());
    details.put("leaseUntil", bot.getLeaseUntil());

    List<Reason> reasons = new ArrayList<>();
    if (!"ERROR".equalsIgnoreCase(bot.getStatus())) {
      reasons.add(new Reason("NOT_IN_ERROR", "Bot status is not ERROR (current=" + bot.getStatus() + ")"));
    }

    commands.findTopByBotInstanceIdAndStatusOrderByCreatedAtDesc(bot.getId(), "FAILED")
        .ifPresentOrElse(
            failed -> details.put("latestFailedCommand", toFailedCommandDetails(failed)),
            () -> reasons.add(new Reason("NO_FAILED_COMMAND", "No FAILED command found for this bot"))
        );

    boolean ok = reasons.isEmpty();
    return new BotError(ok, reasons, details);
  }

  private Map<String, Object> toFailedCommandDetails(BotCommandEntity c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId().toString());
    m.put("command", c.getCommand());
    m.put("status", c.getStatus());
    m.put("attempts", c.getAttempts());
    m.put("workerId", c.getWorkerId());
    m.put("requestId", c.getRequestId());
    m.put("traceparent", c.getTraceparent());
    m.put("errorMessage", c.getErrorMessage());
    m.put("createdAt", c.getCreatedAt());
    m.put("processedAt", c.getProcessedAt());
    return m;
  }

  private int countAliveWorkers(Duration window) {
    Instant after = Instant.now().minus(window);
    try {
      Integer n = jdbc.queryForObject(
          "SELECT COUNT(*) FROM quantor_workers WHERE last_heartbeat_at >= ?",
          Integer.class,
          java.sql.Timestamp.from(after)
      );
      return n == null ? 0 : n;
    } catch (Exception ignored) {
      // if table doesn't exist (dev), return 0 and diagnostics will show NO_WORKERS
      return 0;
    }
  }

  public record Reason(String code, String message) {}

  public record StartEligibility(
      boolean eligible,
      List<Reason> reasons,
      Map<String, Object> details
  ) {}

  public record BotError(
      boolean ok,
      List<Reason> reasons,
      Map<String, Object> details
  ) {}
}
