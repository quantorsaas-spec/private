package com.quantor.infrastructure.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
import com.quantor.application.exchange.Timeframes;
import com.quantor.application.ports.ConfigPort;
import com.quantor.application.ports.NotifierPort;
import com.quantor.application.service.SessionService;
import com.quantor.infrastructure.db.UserSecretsStore;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal Telegram command bot with long-polling via getUpdates.
 *
 * Commands:
 *  /start   - start default session
 *  /pause   - pause default session
 *  /resume  - resume default session
 *  /stop    - stop default session
 *  /status  - show registry status
 *  /health  - health snapshot (last tick/error)
 *  /setkeys - store Binance keys securely in DB (encrypted)
 */
public class TelegramCommandBot {

    private final String botToken;
    private final String chatId;
    private final NotifierPort notifier;
    private final SessionService sessions;
    private final ConfigPort config;
    private final UserSecretsStore secretsStore;
    private final char[] masterPassword;
    private final String userId;

    private enum Awaiting { NONE, BINANCE_API_KEY, BINANCE_API_SECRET }
    private volatile Awaiting awaiting = Awaiting.NONE;
    private volatile String tempApiKey = null;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(70, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper om = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile long offset = 0;

    public TelegramCommandBot(String botToken, String chatId, NotifierPort notifier, SessionService sessions, ConfigPort config,
                             UserSecretsStore secretsStore, char[] masterPassword, String userId) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.notifier = notifier;
        this.sessions = sessions;
        this.config = config;
        this.secretsStore = secretsStore;
        this.masterPassword = masterPassword;
        this.userId = userId;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        safeSend("✅ Quantor Telegram bot started. Use /start /pause /resume /stop /status /health /setkeys");

        while (running.get()) {
            try {
                pollOnce();
            } catch (Exception e) {
                // don't spam Telegram; log locally
                System.err.println("Telegram poll error: " + e.getMessage());
                sleep(1500);
            }
        }
    }

    public void stop() {
        running.set(false);
        safeSend("⛔ Quantor Telegram bot stopped.");
    }

    private void pollOnce() throws Exception {
        String url = "https://api.telegram.org/bot" + botToken + "/getUpdates" +
                "?timeout=60" +
                "&offset=" + offset;
        Request req = new Request.Builder().url(url).get().build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new RuntimeException("HTTP " + resp.code());
            }
            JsonNode root = om.readTree(resp.body().string());
            JsonNode ok = root.get("ok");
            if (ok == null || !ok.asBoolean()) return;

            JsonNode result = root.get("result");
            if (result == null || !result.isArray()) return;

            for (JsonNode upd : result) {
                long updateId = upd.path("update_id").asLong();
                offset = Math.max(offset, updateId + 1);

                JsonNode msg = upd.get("message");
                if (msg == null) continue;

                String fromChatId = msg.path("chat").path("id").asText();
                if (chatId != null && !chatId.isBlank() && !chatId.equals(fromChatId)) {
                    // ignore other chats
                    continue;
                }
                String text = msg.path("text").asText(null);
                if (text == null) continue;
                onCommand(text.trim());
            }
        }
    }

    public void onCommand(String cmd) {
        if (cmd == null || cmd.isBlank()) return;
        // If we are in the middle of /setkeys flow, treat any text as input.
        if (awaiting != Awaiting.NONE && !cmd.trim().startsWith("/")) {
            onSecretInput(cmd.trim());
            return;
        }

        String c = cmd.trim().toLowerCase();

        switch (c) {
            case "/start" -> startDefault();
            case "/pause" -> pauseDefault();
            case "/resume" -> resumeDefault();
            case "/stop" -> stopDefault();
            case "/status" -> safeSend(sessions.statusText());
            case "/health" -> safeSend(sessions.healthText());
            case "/setkeys" -> startSetKeys();
            case "/cancel" -> cancelFlow();
            default -> safeSend("Unknown command: " + cmd + "\nUse /start /pause /resume /stop /status /health /setkeys");
        }
    }

    private void startSetKeys() {
        if (masterPassword == null || masterPassword.length == 0) {
            safeSend("❌ QUANTOR_MASTER_PASSWORD is not set.\n" +
                    "On Windows PowerShell: setx QUANTOR_MASTER_PASSWORD \"your-strong-password\" (then reopen terminal)");
            return;
        }
        if (secretsStore == null) {
            safeSend("❌ Secrets storage is not configured.");
            return;
        }
        awaiting = Awaiting.BINANCE_API_KEY;
        tempApiKey = null;
        safeSend("🔐 /setkeys started.\nSend BINANCE_API_KEY as the next message.\n(Use /cancel to abort)");
    }

    private void cancelFlow() {
        awaiting = Awaiting.NONE;
        tempApiKey = null;
        safeSend("✅ Cancelled.");
    }

    private void onSecretInput(String text) {
        if (text == null) return;

        // Avoid accidental leakage via logs
        String v = text.trim();
        if (v.isBlank()) {
            safeSend("⚠ Empty value. Try again or /cancel.");
            return;
        }

        if (awaiting == Awaiting.BINANCE_API_KEY) {
            tempApiKey = v;
            awaiting = Awaiting.BINANCE_API_SECRET;
            safeSend("✅ API KEY received. Now send BINANCE_API_SECRET.");
            return;
        }

        if (awaiting == Awaiting.BINANCE_API_SECRET) {
            String apiSecret = v;
            try {
                secretsStore.putPlaintext(userId, "BINANCE_API_KEY", tempApiKey, masterPassword);
                secretsStore.putPlaintext(userId, "BINANCE_API_SECRET", apiSecret, masterPassword);
                awaiting = Awaiting.NONE;
                tempApiKey = null;
                safeSend("✅ Binance keys saved securely (encrypted in DB) for user: " + userId);
            } catch (Exception e) {
                awaiting = Awaiting.NONE;
                tempApiKey = null;
                safeSend("❌ Failed to save keys: " + e.getMessage());
            }
        }
    }

    private ExecutionJob defaultJob() {
        String symbol = first("trade.symbol", "symbol", "BTCUSDT");
        String interval = first("trade.interval", "interval", "1m");
        int lookback = config.getInt("trade.lookback", 200);
        String userId = config.get("userId", "local");
        String strategyId = config.get("strategyType", "online");
        ExchangeId ex = ExchangeId.valueOf(config.get("exchange", "BINANCE").trim().toUpperCase());
        ExchangeId md = ExchangeId.valueOf(config.get("paper.marketDataExchange", "BINANCE").trim().toUpperCase());
        if (ex != ExchangeId.PAPER) md = ex;
        return new ExecutionJob(userId, strategyId, ex, md, MarketSymbol.parse(symbol), Timeframes.parse(interval), lookback);
    }

    private long defaultPeriodMs() {
        String interval = first("trade.interval", "interval", "1m");
        return intervalToMs(interval);
    }

    private void startDefault() {
        ExecutionJob job = defaultJob();
        sessions.start(job, defaultPeriodMs());
        safeSend("▶ Started: " + job.key());
    }

    private void pauseDefault() {
        ExecutionJob job = defaultJob();
        sessions.pause(job);
        safeSend("⏸ Paused: " + job.key());
    }

    private void resumeDefault() {
        ExecutionJob job = defaultJob();
        sessions.resume(job);
        safeSend("▶ Resumed: " + job.key());
    }

    private void stopDefault() {
        ExecutionJob job = defaultJob();
        sessions.stop(job);
        safeSend("⛔ Stopped: " + job.key());
    }

    private void safeSend(String msg) {
        try {
            notifier.send(msg);
        } catch (Exception e) {
            System.out.println(msg);
        }
    }

    private String first(String primary, String fallback, String def) {
        String v = config.get(primary, null);
        if (v == null || v.isBlank()) v = config.get(fallback, null);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static long intervalToMs(String interval) {
        if (interval == null) return 60_000L;
        String s = interval.trim().toLowerCase();
        try {
            if (s.endsWith("ms")) return Long.parseLong(s.substring(0, s.length() - 2));
            if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length() - 1)) * 1000L;
            if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60_000L;
            if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3_600_000L;
            if (s.endsWith("d")) return Long.parseLong(s.substring(0, s.length() - 1)) * 86_400_000L;
        } catch (Exception ignore) {}
        return 60_000L;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (Exception ignore) {}
    }
}
