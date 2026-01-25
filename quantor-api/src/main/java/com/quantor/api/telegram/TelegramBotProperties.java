package com.quantor.api.telegram;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Telegram config.
 *
 * IMPORTANT:
 * - This is intended for local/dev MVP (ops bot) only.
 * - Do NOT hardcode tokens in YAML. Use env vars.
 */
@ConfigurationProperties(prefix = "quantor.telegram")
public record TelegramBotProperties(
    boolean enabled,
    String botToken,
    /**
     * Comma-separated list of allowed Telegram user ids.
     * Example: "12345,67890".
     */
    String allowedUserIds,
    /** Optional chat id to which the bot can send notifications in the future. */
    Long opsChatId) {

  public Set<String> allowedUserIdSet() {
    if (allowedUserIds == null || allowedUserIds.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(allowedUserIds.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }
}
