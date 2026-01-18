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
 * Starts the Telegram command bot (long-polling) when enabled via config.
 *
 * application.yml:
 *   quantor:
 *     telegram:
 *       enabled: true
 */
@Component
@ConditionalOnProperty(prefix = "quantor.telegram", name = "enabled", havingValue = "true")
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
        String token = config.getSecret("telegram.botToken");
        String chatId = config.getSecret("telegram.chatId");

        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
            System.err.println("quantor.telegram.enabled=true but Telegram secrets are missing. " +
                    "Set telegram.botToken and telegram.chatId in secrets storage.");
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

        bot.start();
    }
}
