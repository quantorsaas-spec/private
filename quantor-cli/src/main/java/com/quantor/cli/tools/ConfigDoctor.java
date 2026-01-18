package com.quantor.cli.tools;

import com.quantor.application.config.ConfigKey;
import com.quantor.application.config.ConfigValidator;
import com.quantor.application.config.ConfigValidationResult;
import com.quantor.infrastructure.config.FileConfigService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise diagnostics for configuration.
 *
 * Usage:
 *   java -jar quantor-cli.jar validate-config [--profile <name>] [--account <name>] [--full] [--json] [--out <path>]
 *   java -jar quantor-cli.jar doctor [--profile <name>] [--account <name>] [--full] [--json] [--out <path>]
 *
 * Exit codes:
 *   0: OK
 *   2: Problems found
 */
public class ConfigDoctor {

    public static int run(String[] args) {
        String profile = null;
        String account = null;
        boolean full = false;
        boolean json = false;
        Path out = null;

        List<String> tail = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--profile".equalsIgnoreCase(a) && i + 1 < args.length) {
                profile = args[++i];
            } else if ("--account".equalsIgnoreCase(a) && i + 1 < args.length) {
                account = args[++i];
            } else if ("--full".equalsIgnoreCase(a)) {
                full = true;
            } else if ("--json".equalsIgnoreCase(a)) {
                json = true;
            } else if ("--out".equalsIgnoreCase(a) && i + 1 < args.length) {
                out = Path.of(args[++i]);
            } else {
                tail.add(a);
            }
        }

        try {
            FileConfigService cfg = FileConfigService.defaultFromWorkingDir(profile, account);

            if (full) {
                if (json) {
                    String payload = buildFullJson(profile, account, cfg);
                    writeJson(out, payload);
                } else {
                    printEnvironmentReport(profile, account);
                    ConfigPrinter.printMasked(cfg, new String[]{
                            "baseUrlLive", "apiUrl", "log.level"
                    }, "Config (masked)");

                    ConfigPrinter.printMasked(cfg, new String[]{
                            ConfigKey.BINANCE_API_KEY.key(),
                            ConfigKey.BINANCE_API_SECRET.key(),
                            ConfigKey.TELEGRAM_BOT_TOKEN.key(),
                            ConfigKey.TELEGRAM_CHAT_ID.key(),
                            ConfigKey.CHATGPT_API_KEY.key(),
                            "chatGptApiUrl"
                    }, "Secrets (masked)");
                }
            }

            ConfigValidator validator = new ConfigValidator();
            ConfigValidationResult res = validator.validate(cfg);

            int code = res.ok() ? 0 : 2;

            if (json) {
                String payload = buildValidationJson(profile, account, res);
                writeJson(out, payload);
                return code;
            }

            if (res.ok()) {
                System.out.println("✅ Config OK.");
                return 0;
            }

            System.out.println("❌ Config problems:");
            for (String err : res.errors()) {
                System.out.println(" - " + err);
            }
            System.out.println("\nTips:");
            System.out.println(" - Run: java -jar quantor-cli.jar setup" + (profile != null ? " --profile " + profile : ""));
            System.out.println(" - Or run: java -jar quantor-cli.jar configure" + (profile != null ? " --profile " + profile : "") + " init");
            System.out.println(" - Or set env overrides like QUANTOR_BINANCE_API_KEY, QUANTOR_BINANCE_API_SECRET, ...");
            System.out.println(" - If secrets are encrypted, set QUANTOR_MASTER_PASSWORD before running.");
            return 2;

        } catch (Exception e) {
            if (json) {
                String payload = "{\"ok\":false,\"error\":\"" + escape(e.getMessage()) + "\"}";
                writeJson(out, payload);
                return 2;
            }
            System.out.println("❌ Doctor failed: " + e.getMessage());
            return 2;
        }
    }

    private static void writeJson(Path out, String payload) {
        try {
            if (out == null) {
                System.out.println(payload);
                return;
            }
            Files.createDirectories(out.getParent() == null ? Path.of(".") : out.getParent());
            Files.writeString(out, payload + "\n", StandardCharsets.UTF_8);
            System.out.println("Wrote report: " + out.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("❌ Failed to write report: " + e.getMessage());
        }
    }

    private static String buildValidationJson(String profile, String account, ConfigValidationResult res) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"ok\":").append(res.ok());
        sb.append(",\"profile\":").append(str(profile));
        sb.append(",\"account\":").append(str(account));
        sb.append(",\"generated_at_utc\":").append(str(Instant.now().toString()));
        sb.append(",\"errors\":[");
        for (int i = 0; i < res.errors().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(str(res.errors().get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String buildFullJson(String profile, String account, FileConfigService cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"ok\":true");
        sb.append(",\"profile\":").append(str(profile));
        sb.append(",\"account\":").append(str(account));
        sb.append(",\"generated_at_utc\":").append(str(Instant.now().toString()));
        sb.append(",\"environment\":{");
        sb.append("\"java\":").append(str(System.getProperty("java.version")));
        sb.append(",\"os\":").append(str(System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")"));
        sb.append(",\"user\":").append(str(System.getProperty("user.name")));
        sb.append(",\"workdir\":").append(str(System.getProperty("user.dir")));
        sb.append(",\"timezone\":").append(str(java.util.TimeZone.getDefault().getID()));
        sb.append("}");
        sb.append(",\"config_masked\":{");
        appendMasked(sb, cfg, "baseUrlLive");
        sb.append(","); appendMasked(sb, cfg, "apiUrl");
        sb.append(","); appendMasked(sb, cfg, "log.level");
        sb.append("}");
        sb.append(",\"secrets_masked\":{");
        appendMasked(sb, cfg, ConfigKey.BINANCE_API_KEY.key());
        sb.append(","); appendMasked(sb, cfg, ConfigKey.BINANCE_API_SECRET.key());
        sb.append(","); appendMasked(sb, cfg, ConfigKey.TELEGRAM_BOT_TOKEN.key());
        sb.append(","); appendMasked(sb, cfg, ConfigKey.TELEGRAM_CHAT_ID.key());
        sb.append(","); appendMasked(sb, cfg, ConfigKey.CHATGPT_API_KEY.key());
        sb.append(","); appendMasked(sb, cfg, "chatGptApiUrl");
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    private static void appendMasked(StringBuilder sb, FileConfigService cfg, String key) {
        String val = cfg.get(key, "");
        String masked = ConfigPrinter.maskValue(val);
        sb.append(str(key)).append(":").append(str(masked));
    }

    private static void printEnvironmentReport(String profile, String account) {
        System.out.println("=== Quantor Doctor Report ===");
        System.out.println("profile: " + (profile == null ? "<default>" : profile));
        System.out.println("account: " + (account == null ? "<default>" : account));
        System.out.println("java: " + System.getProperty("java.version"));
        System.out.println("os: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        System.out.println("user: " + System.getProperty("user.name"));
        System.out.println("workdir: " + System.getProperty("user.dir"));
        System.out.println("timezone: " + java.util.TimeZone.getDefault().getID());

        Path base = Path.of(System.getProperty("user.dir")).resolve("config");
        Path dir = (profile == null || profile.isBlank()) ? base : base.resolve(profile.trim());

        System.out.println("\nconfigDir: " + dir);
        System.out.println("exists: " + Files.exists(dir));
        System.out.println("config.properties: " + Files.exists(dir.resolve("config.properties")));
        System.out.println("secrets.properties: " + Files.exists(dir.resolve("secrets.properties")));
        System.out.println(".env: " + Files.exists(dir.resolve(".env")));
        System.out.println("masterPassword set: " + (System.getenv("QUANTOR_MASTER_PASSWORD") != null || System.getProperty("quantor.masterPassword") != null));
        System.out.println("=============================");
        System.out.println();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String str(String s) {
        if (s == null) return "null";
        return "\"" + escape(s) + "\"";
    }
}
