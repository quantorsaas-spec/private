package com.quantor.api.telegram;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Telegram config.
 *
 * IMPORTANT:
 * - Local / dev MVP only
 * - Tokens must come from env or secrets.properties
 */
@ConfigurationProperties(prefix = "quantor.telegram")
public record TelegramBotProperties(

    boolean enabled,

    /**
     * Enable Ops (polling) bot.
     * Must be FALSE when any other Telegram bot is running.
     */
    boolean opsEnabled,

    String botToken,

    /**
     * Comma-separated list of allowed Telegram user ids.
     * Example: "12345,67890"
     */
    String allowedUserIds,

    /**
     * Optional chat id for future notifications
     */
    Long opsChatId

) {

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
