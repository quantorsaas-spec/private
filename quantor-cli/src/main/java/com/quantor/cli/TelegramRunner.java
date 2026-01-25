package com.quantor.cli;

import com.quantor.application.ports.ConfigPort;
import com.quantor.application.ports.NotifierPort;
import com.quantor.application.service.SessionService;
import com.quantor.cli.bootstrap.Bootstrap;
import com.quantor.infrastructure.config.FileConfigService;
import com.quantor.infrastructure.db.UserSecretsStore;
import com.quantor.infrastructure.notification.TelegramNotifier;
import com.quantor.infrastructure.telegram.TelegramCommandBot;

import java.io.IOException;

/**
 * Runs Quantor in Telegram mode (long-polling).
 *
 * Usage:
 *   java -jar quantor-cli.jar telegram
 */
public final class TelegramRunner {

    private TelegramRunner() {}

    public static int run(String[] args) {
        ConfigPort config;
        try {
            config = FileConfigService.defaultFromWorkingDir();
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
            return 1;
        }

        String token = config.getSecret("telegram.botToken");
        String chatId = config.getSecret("telegram.chatId");

        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
            System.err.println("Telegram is not configured. Set telegram.botToken and telegram.chatId secrets (DB preferred).\n" +
                    "Tip: set QUANTOR_MASTER_PASSWORD env var and put secrets in config/secrets.properties once; they will be migrated to DB.");
            return 2;
        }

        // Force Telegram notifications for this mode
        NotifierPort notifier = new TelegramNotifier(token, chatId);
        SessionService sessions = Bootstrap.createSessionService(config);

        String master = System.getenv("QUANTOR_MASTER_PASSWORD");
        char[] masterPassword = (master == null) ? new char[0] : master.toCharArray();
        String userId = config.get("userId", "local");
        UserSecretsStore secretsStore = new UserSecretsStore();

        TelegramCommandBot bot = new TelegramCommandBot(token, chatId, notifier, sessions, config, secretsStore, masterPassword, userId);
        bot.start();
        return 0;
    }
}
