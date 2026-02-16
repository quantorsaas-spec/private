// File: quantor-cli/src/main/java/com/quantor/cli/TelegramRunner.java
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
import java.util.Arrays;

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

        // Optional: allow stopping after N seconds (useful for smoke tests)
        int autoStopSeconds = parseAutoStopSeconds(args);

        String token = config.getSecret("telegram.botToken");
        String chatId = config.getSecret("telegram.chatId");

        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
            System.err.println(
                    "Telegram is not configured. Set telegram.botToken and telegram.chatId secrets (DB preferred).\n" +
                    "Tip: set QUANTOR_MASTER_PASSWORD env var and put secrets in config/secrets.properties once; they will be migrated to DB."
            );
            return 2;
        }

        // notifier used by SessionService + TelegramCommandBot.safeSend()
        NotifierPort notifier = new TelegramNotifier(token, chatId);

        // Session service (enforces trading.enabled in CORE for start/resume)
        SessionService sessions = Bootstrap.createSessionService(config);

        // master password used for /setkeys (DB encryption)
        String master = System.getenv("QUANTOR_MASTER_PASSWORD");
        char[] masterPassword = (master == null) ? new char[0] : master.toCharArray();

        String userId = config.get("userId", "local");

        UserSecretsStore secretsStore = new UserSecretsStore();

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

        // Informational banner (do NOT block starting the bot; it must answer /config /status /health)
        boolean tradingEnabled = Boolean.parseBoolean(config.get("trading.enabled", "true"));
        String disabledReason = config.get("trading.disabledReason", "");
        if (!tradingEnabled) {
            String reason = (disabledReason == null || disabledReason.isBlank()) ? "Trading disabled" : disabledReason;
            try { notifier.send("🛑 Trading disabled: " + reason); } catch (Exception ignore) {}
            System.out.println("TRADING DISABLED: " + reason);
        }

        // Smoke-test helper: stop bot after N seconds
        if (autoStopSeconds > 0) {
            Thread t = new Thread(() -> {
                try { Thread.sleep(autoStopSeconds * 1000L); } catch (InterruptedException ignore) {}
                try { bot.stop(); } catch (Exception ignore) {}
            }, "telegram-autostop");
            t.setDaemon(true);
            t.start();
        }

        bot.start();
        return 0;
    }

    private static int parseAutoStopSeconds(String[] args) {
        if (args == null || args.length == 0) return 0;
        // supports: telegram --autostop 30
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a == null) continue;
            a = a.trim();
            if ("--autostop".equalsIgnoreCase(a) && i + 1 < args.length) {
                String v = args[i + 1];
                try { return Math.max(0, Integer.parseInt(v.trim())); } catch (Exception ignore) { return 0; }
            }
        }
        // also support: telegram --autostop=30
        return Arrays.stream(args)
                .filter(s -> s != null && s.toLowerCase().startsWith("--autostop="))
                .map(s -> s.substring("--autostop=".length()))
                .map(String::trim)
                .map(v -> {
                    try { return Integer.parseInt(v); } catch (Exception e) { return 0; }
                })
                .findFirst()
                .orElse(0);
    }
}
