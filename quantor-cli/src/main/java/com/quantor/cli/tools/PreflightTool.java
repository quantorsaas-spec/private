package com.quantor.cli.tools;

import com.quantor.infrastructure.config.FileConfigService;
import com.quantor.exchange.HttpClient;
import okhttp3.Headers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise+ preflight checks.
 *
 * Checks:
 *  - Binance ping
 *  - Telegram getMe (WARN if token missing; FAIL if request fails)
 *  - Telegram sendMessage (optional; FAIL if enabled and can't send)
 *  - ChatGPT config sanity (WARN on partial config)
 *
 * Flags:
 *  --profile <name>
 *  --account <name>
 *  --json
 *  --out <file>
 *  --fail-on-warn
 *  --send-test-message
 *  --message <text>
 *
 * Exit codes:
 *  0 OK
 *  1 FAIL (any failures)
 *  2 WARN (only when --fail-on-warn and warnings exist)
 */
public final class PreflightTool {

    private PreflightTool() {}

    public static int run(String[] args) throws Exception {
        String profile = null;
        String account = null;

        boolean jsonOut = false;
        String outPath = null;
        boolean failOnWarn = false;

        boolean sendTestMessage = false;
        String testMessage = "Quantor preflight: Telegram test message ✅";

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--profile".equalsIgnoreCase(a) && i + 1 < args.length) {
                profile = args[++i];
            } else if ("--account".equalsIgnoreCase(a) && i + 1 < args.length) {
                account = args[++i];
            } else if ("--json".equalsIgnoreCase(a)) {
                jsonOut = true;
            } else if ("--out".equalsIgnoreCase(a) && i + 1 < args.length) {
                outPath = args[++i];
            } else if ("--fail-on-warn".equalsIgnoreCase(a)) {
                failOnWarn = true;
            } else if ("--send-test-message".equalsIgnoreCase(a)) {
                sendTestMessage = true;
            } else if ("--message".equalsIgnoreCase(a) && i + 1 < args.length) {
                testMessage = args[++i];
            }
        }

        FileConfigService cfg = FileConfigService.defaultFromWorkingDir(profile, account);
        HttpClient http = new HttpClient();

        List<Check> checks = new ArrayList<>();
        int failures = 0;
        int warnings = 0;

        // 1) Binance ping
        try {
            String base = cfg.get("baseUrlLive", "https://api.binance.com").trim();
            String url = base + "/api/v3/ping";
            http.get(url, new Headers.Builder().build());
            if (!jsonOut) System.out.println("[OK] Binance ping " + url);
            checks.add(new Check("binance_ping", "OK", url, null));
        } catch (Exception e) {
            failures++;
            if (!jsonOut) System.out.println("[FAIL] Binance ping: " + e.getMessage());
            checks.add(new Check("binance_ping", "FAIL", null, e.getMessage()));
        }

        // 2) Telegram getMe
        try {
            String token = cfg.getSecret("TELEGRAM_BOT_TOKEN", "").trim();
            if (token.isEmpty()) {
                warnings++;
                if (!jsonOut) System.out.println("[WARN] Telegram getMe: TELEGRAM_BOT_TOKEN is empty (skipped)");
                checks.add(new Check("telegram_getMe", "WARN", "TELEGRAM_BOT_TOKEN is empty (skipped)", null));
            } else {
                String url = "https://api.telegram.org/bot" + token + "/getMe";
                http.get(url, new Headers.Builder().build());
                if (!jsonOut) System.out.println("[OK] Telegram getMe");
                checks.add(new Check("telegram_getMe", "OK", null, null));
            }
        } catch (Exception e) {
            failures++;
            if (!jsonOut) System.out.println("[FAIL] Telegram getMe: " + e.getMessage());
            checks.add(new Check("telegram_getMe", "FAIL", null, e.getMessage()));
        }

        // 3) Telegram sendMessage (optional)
        if (sendTestMessage) {
            try {
                String token = cfg.getSecret("TELEGRAM_BOT_TOKEN", "").trim();
                String chatId = cfg.getSecret("TELEGRAM_CHAT_ID", "").trim();
                if (token.isEmpty()) throw new IllegalStateException("TELEGRAM_BOT_TOKEN is empty");
                if (chatId.isEmpty()) throw new IllegalStateException("TELEGRAM_CHAT_ID is empty");

                String text = URLEncoder.encode(testMessage, StandardCharsets.UTF_8);
                String url = "https://api.telegram.org/bot" + token + "/sendMessage?chat_id=" + chatId + "&text=" + text;
                http.get(url, new Headers.Builder().build());
                if (!jsonOut) System.out.println("[OK] Telegram sendMessage to chatId=" + chatId);
                checks.add(new Check("telegram_sendMessage", "OK", chatId, null));
            } catch (Exception e) {
                failures++;
                if (!jsonOut) System.out.println("[FAIL] Telegram sendMessage: " + e.getMessage());
                checks.add(new Check("telegram_sendMessage", "FAIL", null, e.getMessage()));
            }
        }

        // 4) ChatGPT config sanity
        try {
            String apiUrl = cfg.getSecret("chatGptApiUrl", "").trim();
            String apiKey = cfg.getSecret("CHATGPT_API_KEY", "").trim();

            if (apiUrl.isEmpty() && apiKey.isEmpty()) {
                if (!jsonOut) System.out.println("[OK] ChatGPT: not configured (skipped)");
                checks.add(new Check("chatgpt_config", "OK", null, null));
            } else if (apiUrl.isEmpty()) {
                warnings++;
                if (!jsonOut) System.out.println("[WARN] ChatGPT: CHATGPT_API_KEY is set, but chatGptApiUrl is empty");
                checks.add(new Check("chatgpt_config", "WARN", "CHATGPT_API_KEY set but chatGptApiUrl empty", null));
            } else if (apiKey.isEmpty()) {
                warnings++;
                if (!jsonOut) System.out.println("[WARN] ChatGPT: chatGptApiUrl is set, but CHATGPT_API_KEY is empty");
                checks.add(new Check("chatgpt_config", "WARN", "chatGptApiUrl set but CHATGPT_API_KEY empty", null));
            } else {
                if (!apiUrl.startsWith("http://") && !apiUrl.startsWith("https://")) {
                    throw new IllegalArgumentException("chatGptApiUrl must start with http:// or https://");
                }
                if (!jsonOut) System.out.println("[OK] ChatGPT config looks valid");
                checks.add(new Check("chatgpt_config", "OK", apiUrl, null));
            }
        } catch (Exception e) {
            failures++;
            if (!jsonOut) System.out.println("[FAIL] ChatGPT config: " + e.getMessage());
            checks.add(new Check("chatgpt_config", "FAIL", null, e.getMessage()));
        }

        boolean ok = failures == 0 && (!failOnWarn || warnings == 0);
        String json = toJson(profile, account, checks, ok, failures, warnings);

        if (jsonOut) {
            System.out.println(json);
        }

        if (outPath != null && !outPath.isBlank()) {
            Path out = Path.of(outPath);
            Path parent = out.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(out, json, StandardCharsets.UTF_8);
            if (!jsonOut) System.out.println("✅ wrote report to " + out.toAbsolutePath());
        }

        if (failures > 0) return 1;
        if (failOnWarn && warnings > 0) return 2;
        return 0;
    }

    static final class Check {
        final String name;
        final String status; // OK, WARN, FAIL
        final String detail;
        final String error;

        Check(String name, String status, String detail, String error) {
            this.name = name;
            this.status = status;
            this.detail = detail;
            this.error = error;
        }
    }

    private static String toJson(String profile, String account, List<Check> checks, boolean ok, int failures, int warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"ok\": ").append(ok).append(",\n");
        if (profile != null && !profile.isBlank()) sb.append("  \"profile\": \"").append(esc(profile)).append("\",\n");
        if (account != null && !account.isBlank()) sb.append("  \"account\": \"").append(esc(account)).append("\",\n");
        sb.append("  \"failures\": ").append(failures).append(",\n");
        sb.append("  \"warnings\": ").append(warnings).append(",\n");
        sb.append("  \"generated_at_utc\": \"").append(Instant.now().toString()).append("\",\n");
        sb.append("  \"checks\": [\n");
        for (int i = 0; i < checks.size(); i++) {
            Check c = checks.get(i);
            sb.append("    {");
            sb.append("\"name\": \"").append(esc(c.name)).append("\", ");
            sb.append("\"status\": \"").append(esc(c.status)).append("\"");
            if (c.detail != null) sb.append(", \"detail\": \"").append(esc(c.detail)).append("\"");
            if (c.error != null) sb.append(", \"error\": \"").append(esc(c.error)).append("\"");
            sb.append("}");
            if (i + 1 < checks.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
