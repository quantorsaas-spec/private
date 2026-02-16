// File: quantor-infrastructure/src/main/java/com/quantor/infrastructure/telegram/TelegramCommandBot.java
package com.quantor.infrastructure.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframes;
import com.quantor.application.guard.SubscriptionRequiredException;
import com.quantor.application.ports.ConfigPort;
import com.quantor.application.ports.NotifierPort;
import com.quantor.application.service.SessionService;
import com.quantor.infrastructure.db.UserSecretsStore;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
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
 *  /config  - show effective config snapshot (NO SECRETS)
 *  /upgrade - show checkout link for PRO subscription
 *
 * P0 RULE:
 * - userId must be provided explicitly (UUID). No silent fallback to "local".
 */
public class TelegramCommandBot {

    private final String botToken;
    private final String chatId;
    private final NotifierPort notifier;
    private final SessionService sessions;
    private final ConfigPort config;
    private final UserSecretsStore secretsStore;
    private final char[] masterPassword;

    /** Explicit runtime userId (UUID string). */
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

    public TelegramCommandBot(String botToken,
                             String chatId,
                             NotifierPort notifier,
                             SessionService sessions,
                             ConfigPort config,
                             UserSecretsStore secretsStore,
                             char[] masterPassword,
                             String userId) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.notifier = notifier;
        this.sessions = sessions;
        this.config = config;
        this.secretsStore = secretsStore;
        this.masterPassword = masterPassword;

        // P0: explicit runtime userId, no "local" fallback
        this.userId = requireUuid(userId, "userId");
        System.out.println(">>> TELEGRAM BOT USER ID = " + this.userId);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        safeSend("✅ Quantor Telegram bot started. Use /start /pause /resume /stop /status /health /setkeys /config /upgrade");

        while (running.get()) {
            try {
                pollOnce();
            } catch (Exception e) {
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

        if (awaiting != Awaiting.NONE && !cmd.trim().startsWith("/")) {
            onSecretInput(cmd.trim());
            return;
        }

        String c = cmd.trim().toLowerCase(Locale.ROOT);

        switch (c) {
            case "/start" -> startDefault();
            case "/pause" -> pauseDefault();
            case "/resume" -> resumeDefault();
            case "/stop" -> stopDefault();
            case "/status" -> safeSend(sessions.statusText());
            case "/health" -> safeSend(sessions.healthText());
            case "/setkeys" -> startSetKeys();
            case "/cancel" -> cancelFlow();
            case "/config" -> safeSend(configSnapshot());
            case "/upgrade" -> sendUpgradeLink();
            default -> safeSend("Unknown command: " + cmd + "\nUse /start /pause /resume /stop /status /health /setkeys /config /upgrade");
        }
    }

    // ===== Billing / upgrade =====
    private void sendUpgradeLink() {
        String base = config.get("billing.checkoutUrl", "");
        if (base == null || base.isBlank()) {
            safeSend("⚠ Checkout URL is not configured.\nSet billing.checkoutUrl in config.properties.");
            return;
        }

        // userId is already validated UUID in ctor, but keep safety
        String uid = this.userId;
        try {
            UUID.fromString(uid.trim());
        } catch (Exception e) {
            safeSend("⚠ userId must be UUID.\nFix runtime wiring (passed userId).");
            safeSend("Checkout link (without binding):\n" + base);
            return;
        }

        // IMPORTANT:
        // Use Lemon Squeezy "custom data" so it appears in webhook payload as meta.custom_data.user_id
        String sep = base.contains("?") ? "&" : "?";
        String url = base + sep + "checkout[custom][user_id]=" + URLEncoder.encode(uid.trim(), StandardCharsets.UTF_8);

        safeSend("🔒 To enable trading, upgrade to PRO:\n" + url);
    }

    // ===== STOP-FIX: global trading kill-switch =====
    private boolean isTradingEnabledOrNotify() {
        boolean enabled = Boolean.parseBoolean(config.get("trading.enabled", "true"));
        if (enabled) return true;

        String reason = config.get("trading.disabledReason", "Trading disabled");
        safeSend("🛑 Trading disabled: " + reason);
        return false;
    }

    // ===== STOP-FIX: LIVE gate (avoid accidental real trading) =====
    private boolean isLiveAllowedOrNotify(ExecutionJob job) {
        boolean liveEnabled = Boolean.parseBoolean(config.get("liveRealTradingEnabled", "false"));
        if (!liveEnabled) return true;

        String apiKey = firstSecret("BINANCE_API_KEY", "binance.apiKey", "apiKey");
        String apiSecret = firstSecret("BINANCE_API_SECRET", "binance.apiSecret", "apiSecret");

        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            safeSend("🛑 LIVE blocked: Binance keys not set. Use /setkeys.");
            return false;
        }
        return true;
    }

    private void startSetKeys() {
        if (masterPassword == null || masterPassword.length == 0) {
            safeSend("❌ QUANTOR_MASTER_PASSWORD is not set.");
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
        String symbol = first("trade.symbol", "symbol", "BTC/USDT");
        String interval = first("trade.interval", "interval", "1m");
        int lookback = config.getInt("trade.lookback", 200);

        // P0: always use runtime userId (validated UUID)
        String uid = this.userId;

        String strategyId = config.get("strategyType", "online");

        String exRaw = config.get("trade.exchange", null);
        if (exRaw == null || exRaw.isBlank()) exRaw = config.get("exchange", "BINANCE");
        ExchangeId ex = ExchangeId.valueOf(exRaw.trim().toUpperCase(Locale.ROOT));

        ExchangeId md = ExchangeId.valueOf(config.get("paper.marketDataExchange", "BINANCE").trim().toUpperCase(Locale.ROOT));
        if (ex != ExchangeId.PAPER) md = ex;

        return new ExecutionJob(uid, strategyId, ex, md, MarketSymbol.parse(symbol), Timeframes.parse(interval), lookback);
    }

    private long defaultPeriodMs() {
        String interval = first("trade.interval", "interval", "1m");
        return intervalToMs(interval);
    }

    private void startDefault() {
        if (!isTradingEnabledOrNotify()) return;

        ExecutionJob job = defaultJob();
        if (!isLiveAllowedOrNotify(job)) return;

        try {
            sessions.start(job, defaultPeriodMs());
            safeSend("▶ Started: " + job.key());
        } catch (SubscriptionRequiredException e) {
            safeSend("🔒 Subscription required to start trading.\nUse /upgrade to activate PRO.");
            sendUpgradeLink();
        } catch (Exception e) {
            safeSend("❌ Failed to start: " + safeMessage(e));
        }
    }

    private void pauseDefault() {
        ExecutionJob job = defaultJob();
        sessions.pause(job);
        safeSend("⏸ Paused: " + job.key());
    }

    private void resumeDefault() {
        if (!isTradingEnabledOrNotify()) return;

        ExecutionJob job = defaultJob();
        if (!isLiveAllowedOrNotify(job)) return;

        try {
            sessions.resume(job);
            safeSend("▶ Resumed: " + job.key());
        } catch (SubscriptionRequiredException e) {
            safeSend("🔒 Subscription required to resume trading.\nUse /upgrade to activate PRO.");
            sendUpgradeLink();
        } catch (Exception e) {
            safeSend("❌ Failed to resume: " + safeMessage(e));
        }
    }

    private void stopDefault() {
        ExecutionJob job = defaultJob();
        sessions.stop(job);
        safeSend("⛔ Stopped: " + job.key());
    }

    private String safeMessage(Exception e) {
        if (e == null) return "unknown";
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? e.getClass().getSimpleName() : m;
    }

    private String configSnapshot() {
        // NO SECRETS here.
        StringBuilder sb = new StringBuilder();
        sb.append("Config snapshot (no secrets)\n");

        // show runtime userId (authoritative)
        sb.append("- userId=").append(this.userId).append("\n");
        sb.append("- mode=").append(config.get("mode", "TEST")).append("\n");

        sb.append("- trading.enabled=").append(config.get("trading.enabled", "true")).append("\n");
        sb.append("- trading.disabledReason=").append(config.get("trading.disabledReason", "")).append("\n");

        sb.append("- liveRealTradingEnabled=").append(config.get("liveRealTradingEnabled", "false")).append("\n");

        sb.append("- billing.checkoutUrl=").append(config.get("billing.checkoutUrl", "")).append("\n");

        String exRaw = config.get("trade.exchange", null);
        if (exRaw == null || exRaw.isBlank()) exRaw = config.get("exchange", "BINANCE");
        sb.append("- exchange=").append(exRaw).append("\n");

        sb.append("- paper.marketDataExchange=").append(config.get("paper.marketDataExchange", "BINANCE")).append("\n");

        sb.append("- trade.symbol=").append(first("trade.symbol", "symbol", "BTC/USDT")).append("\n");
        sb.append("- trade.interval=").append(first("trade.interval", "interval", "1m")).append("\n");
        sb.append("- trade.lookback=").append(config.getInt("trade.lookback", 200)).append("\n");
        sb.append("- strategyType=").append(config.get("strategyType", "online")).append("\n");

        return sb.toString().trim();
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

    private String firstSecret(String... keys) {
        if (keys == null) return null;
        for (String k : keys) {
            if (k == null || k.isBlank()) continue;
            try {
                String v = config.getSecret(k);
                if (v != null && !v.isBlank()) return v;
            } catch (Exception ignore) {
                // decrypt fail => treat as missing
            }
        }
        return null;
    }

    private static long intervalToMs(String interval) {
        if (interval == null) return 60_000L;
        String s = interval.trim().toLowerCase(Locale.ROOT);
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

    private static String requireUuid(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(name + " is blank (must be UUID)");
        }
        String v = raw.trim();
        try {
            UUID.fromString(v);
            return v;
        } catch (Exception e) {
            throw new IllegalArgumentException(name + " must be UUID, got: " + raw);
        }
    }
}
