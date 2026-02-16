package com.quantor.api.wiring;

import com.quantor.application.ports.ConfigPort;
import com.quantor.application.ports.NotifierPort;
import com.quantor.application.service.SessionService;
import com.quantor.infrastructure.db.UserSecretsStore;
import com.quantor.infrastructure.notification.TelegramNotifier;
import com.quantor.infrastructure.telegram.TelegramCommandBot;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Starts TelegramCommandBot (long-polling) ONLY when explicitly enabled.
 *
 * This must NOT share the same enable-flag with TelegramOpsBot,
 * otherwise you'll get Telegram 409 (two getUpdates consumers).
 */
@Component
@ConditionalOnProperty(prefix = "quantor.telegram.command", name = "enabled", havingValue = "true")
public class TelegramBotStartup implements ApplicationRunner {

    private final ConfigPort config;
    private final SessionService sessions;
    private final UserSecretsStore secretsStore;

    public TelegramBotStartup(ConfigPort config, SessionService sessions, UserSecretsStore secretsStore) {
        this.config = config;
        this.sessions = sessions;
        this.secretsStore = secretsStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 1) Prefer env vars (docker/.env)
        String token = getenv("QUANTOR_TELEGRAM_BOT_TOKEN");
        String chatId = getenv("QUANTOR_TELEGRAM_CHAT_ID"); // IMPORTANT: chat id (not admin chat id)

        // 2) Fallback to config secrets
        if (isBlank(token)) token = safeGetSecret("telegram.botToken");
        if (isBlank(chatId)) chatId = safeGetSecret("telegram.chatId");

        if (isBlank(token) || isBlank(chatId)) {
            System.err.println("TelegramCommandBot enabled, but token/chatId missing. Set env: QUANTOR_TELEGRAM_BOT_TOKEN and QUANTOR_TELEGRAM_CHAT_ID");
            return;
        }

        NotifierPort notifier = new TelegramNotifier(token, chatId);

        String master = System.getenv("QUANTOR_MASTER_PASSWORD");
        char[] masterPassword = (master == null) ? new char[0] : master.toCharArray();

        String userId = config.get("userId", "local");

        TelegramCommandBot bot = new TelegramCommandBot(
                token,
                chatId,
                notifier,
                sessions,
                config,
                secretsStore,
                masterPassword,
                userId
        );

        // Do NOT block Spring startup
        Thread t = new Thread(bot::start, "quantor-telegram-command-bot");
        t.setDaemon(true);
        t.start();

        System.out.println("TelegramCommandBot started (long-polling).");
    }

    private static String getenv(String k) {
        String v = System.getenv(k);
        return v == null ? "" : v.trim();
    }

    private String safeGetSecret(String key) {
        try {
            String v = config.getSecret(key);
            return v == null ? "" : v.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
