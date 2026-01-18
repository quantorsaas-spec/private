package com.quantor.cli.tools;

import com.quantor.application.config.ConfigKey;
import com.quantor.infrastructure.config.FileConfigService;
import com.quantor.infrastructure.security.AesGcmCrypto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Enterprise configuration tool (set/get/list/init/export) with profiles + accounts.
 *
 * Global options:
 *   --profile <name>     (default: <workdir>/config)
 *   --account <name>     (default: none; enables config/<profile>/accounts/<account>/ overrides)
 *   --non-interactive    (fails instead of opening interactive menu)
 *
 * Subcommands:
 *   init
 *   list [--all] [--json] [--out <path>]
 *   get <KEY> [--json] [--out <path>]
 *   set <KEY> <VALUE> [--secret] [--encrypt] [--json] [--out <path>]
 *   export [--all] [--json] [--out <path>]
 */
public class ConfigureTool {

    public static int run(String[] args) {
        String profile = null;
        String account = null;
        boolean nonInteractive = false;

        List<String> tail = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--profile".equalsIgnoreCase(a) && i + 1 < args.length) {
                profile = args[++i];
            } else if ("--account".equalsIgnoreCase(a) && i + 1 < args.length) {
                account = args[++i];
            } else if ("--non-interactive".equalsIgnoreCase(a)) {
                nonInteractive = true;
            } else {
                tail.add(a);
            }
        }

        try {
            if (tail.isEmpty()) {
                if (nonInteractive) {
                    System.out.println("❌ Non-interactive mode: missing subcommand.");
                    printUsage();
                    return 2;
                }
                return interactiveMenu(profile, account);
            }

            String sub = tail.get(0).trim().toLowerCase();
            String[] subTail = tail.size() > 1 ? tail.subList(1, tail.size()).toArray(new String[0]) : new String[0];

            switch (sub) {
                case "init":
                    return init(profile, account, subTail);
                case "list":
                    return list(profile, account, subTail);
                case "get":
                    return get(profile, account, subTail);
                case "set":
                    return set(profile, account, subTail);
                case "export":
                    return export(profile, account, subTail);
                default:
                    System.out.println("Unknown configure command: " + sub);
                    printUsage();
                    return 1;
            }
        } catch (Exception e) {
            System.out.println("❌ configure failed: " + e.getMessage());
            return 1;
        }
    }

    private static int interactiveMenu(String profile, String account) throws Exception {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("=== Quantor Configure" + (profile == null ? "" : " [profile=" + profile + "]") + (account == null ? "" : " [account=" + account + "]") + " ===");
            System.out.println("1) init templates");
            System.out.println("2) list keys");
            System.out.println("3) get key");
            System.out.println("4) set key");
            System.out.println("5) export (masked)");
            System.out.println("6) preflight");
            System.out.println("7) doctor");
            System.out.println("0) exit");
            System.out.print("> ");
            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1":
                    init(profile, account, new String[0]);
                    break;
                case "2":
                    list(profile, account, new String[]{"--all"});
                    break;
                case "3":
                    System.out.print("Key: ");
                    String k = sc.nextLine().trim();
                    get(profile, account, new String[]{k});
                    break;
                case "4":
                    System.out.print("Key: ");
                    String key = sc.nextLine().trim();
                    System.out.print("Value: ");
                    String value = sc.nextLine();
                    System.out.print("Secret? (y/N): ");
                    boolean secret = "y".equalsIgnoreCase(sc.nextLine().trim());
                    System.out.print("Encrypt? (y/N): ");
                    boolean encrypt = "y".equalsIgnoreCase(sc.nextLine().trim());
                    List<String> args = new ArrayList<>();
                    args.add(key);
                    args.add(value);
                    if (secret) args.add("--secret");
                    if (encrypt) args.add("--encrypt");
                    set(profile, account, args.toArray(new String[0]));
                    break;
                case "5":
                    export(profile, account, new String[]{"--all", "--json"});
                    break;
                case "6":
                    PreflightTool.run(profile == null ? new String[]{} : new String[]{"--profile", profile});
                    break;
                case "7":
                    List<String> d = new ArrayList<>();
                    if (profile != null) { d.add("--profile"); d.add(profile); }
                    if (account != null) { d.add("--account"); d.add(account); }
                    d.add("--full");
                    ConfigDoctor.run(d.toArray(new String[0]));
                    break;
                case "0":
                    return 0;
                default:
                    System.out.println("Unknown choice.");
            }

            System.out.println();
        }
    }

    private static int export(String profile, String account, String[] args) throws Exception {
        boolean all = false;
        boolean json = false;
        Path out = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--all".equalsIgnoreCase(a)) all = true;
            else if ("--json".equalsIgnoreCase(a)) json = true;
            else if ("--out".equalsIgnoreCase(a) && i + 1 < args.length) out = Path.of(args[++i]);
        }

        FileConfigService cfg = FileConfigService.defaultFromWorkingDir(profile, account);

        // Export masked effective config. For safety, always mask values.
        Map<String, String> kv = new TreeMap<>();
        for (ConfigKey k : ConfigKey.values()) {
            kv.put(k.key(), ConfigPrinter.maskValue(cfg.get(k.key(), "")));
        }
        kv.put("chatGptApiUrl", ConfigPrinter.maskValue(cfg.get("chatGptApiUrl", "")));
        kv.put("baseUrlLive", ConfigPrinter.maskValue(cfg.get("baseUrlLive", "")));
        kv.put("apiUrl", ConfigPrinter.maskValue(cfg.get("apiUrl", "")));
        kv.put("log.level", ConfigPrinter.maskValue(cfg.get("log.level", "")));

        if (json) {
            String payload = toJson(profile, account, kv);
            writeOut(out, payload);
            return 0;
        }

        System.out.println("=== Quantor Effective Config (masked) ===");
        System.out.println("profile: " + (profile == null ? "<default>" : profile));
        System.out.println("account: " + (account == null ? "<default>" : account));
        for (Map.Entry<String, String> e : kv.entrySet()) {
            if (!all && e.getValue() == null) continue;
            System.out.println(e.getKey() + "=" + e.getValue());
        }
        return 0;
    }

    private static String toJson(String profile, String account, Map<String, String> kv) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"ok\":true");
        sb.append(",\"profile\":").append(str(profile));
        sb.append(",\"account\":").append(str(account));
        sb.append(",\"generated_at_utc\":").append(str(Instant.now().toString()));
        sb.append(",\"config_masked\":{");
        int i = 0;
        for (Map.Entry<String, String> e : kv.entrySet()) {
            if (i++ > 0) sb.append(",");
            sb.append(str(e.getKey())).append(":").append(str(e.getValue()));
        }
        sb.append("}}");
        return sb.toString();
    }

    private static void writeOut(Path out, String payload) throws Exception {
        if (out == null) {
            System.out.println(payload);
            return;
        }
        Files.createDirectories(out.getParent() == null ? Path.of(".") : out.getParent());
        Files.writeString(out, payload + "\n", StandardCharsets.UTF_8);
        System.out.println("Wrote report: " + out.toAbsolutePath());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String str(String s) {
        if (s == null) return "null";
        return "\"" + escape(s) + "\"";
    }

    // ----- Existing methods below (init/list/get/set + file resolution) -----

    private static Path resolveProfileDir(String profile) throws IOException {
        Path base = Path.of(System.getProperty("user.dir")).resolve("config");
        if (profile != null && !profile.isBlank()) base = base.resolve(profile.trim());
        Files.createDirectories(base);
        return base;
    }

    private static Path resolveAccountDir(String profile, String account) throws IOException {
        Path dir = resolveProfileDir(profile);
        if (account == null || account.isBlank()) return null;
        Path acc = dir.resolve("accounts").resolve(account.trim());
        Files.createDirectories(acc);
        return acc;
    }

    private static int init(String profile, String account, String[] args) throws IOException {
        Path profileDir = resolveProfileDir(profile);
        Path accountDir = resolveAccountDir(profile, account);

        // Main config templates
        Path cfgExample = profileDir.resolve("config.properties.example");
        Path cfg = profileDir.resolve("config.properties");
        if (!Files.exists(cfgExample)) {
            Files.writeString(cfgExample, "# Quantor config\nbaseUrlLive=https://api.binance.com\nlog.level=INFO\n", StandardCharsets.UTF_8);
        }
        if (!Files.exists(cfg)) Files.copy(cfgExample, cfg);

        // Secrets templates
        Path secExample = profileDir.resolve("secrets.properties.example");
        Path sec = profileDir.resolve("secrets.properties");
        if (!Files.exists(secExample)) {
            Files.writeString(secExample,
                    "# Quantor secrets (DO NOT COMMIT)\nchatGptApiUrl=\nTELEGRAM_BOT_TOKEN=\nTELEGRAM_CHAT_ID=\nBINANCE_API_KEY=\nBINANCE_API_SECRET=\nCHATGPT_API_KEY=\n",
                    StandardCharsets.UTF_8);
        }
        if (!Files.exists(sec)) Files.copy(secExample, sec);

        // .env template
        Path env = profileDir.resolve(".env");
        if (!Files.exists(env)) {
            Files.writeString(env, "# Optional overrides\n# QUANTOR_MASTER_PASSWORD=\n", StandardCharsets.UTF_8);
        }

        // Account templates
        if (accountDir != null) {
            Path aSecExample = accountDir.resolve("secrets.properties.example");
            Path aSec = accountDir.resolve("secrets.properties");
            if (!Files.exists(aSecExample)) {
                Files.writeString(aSecExample,
                        "# Quantor secrets for account '" + account + "' (DO NOT COMMIT)\nchatGptApiUrl=\nTELEGRAM_BOT_TOKEN=\nTELEGRAM_CHAT_ID=\nBINANCE_API_KEY=\nBINANCE_API_SECRET=\nCHATGPT_API_KEY=\n",
                        StandardCharsets.UTF_8);
            }
            if (!Files.exists(aSec)) Files.copy(aSecExample, aSec);
            Path aEnv = accountDir.resolve(".env");
            if (!Files.exists(aEnv)) Files.writeString(aEnv, "# Account overrides\n", StandardCharsets.UTF_8);
        }

        // Audit dir
        Files.createDirectories(profileDir.resolve("audit"));
        if (!Files.exists(profileDir.resolve("audit").resolve("audit.log"))) {
            Files.writeString(profileDir.resolve("audit").resolve("audit.log"), "", StandardCharsets.UTF_8);
        }

        System.out.println("✅ Initialized templates in: " + profileDir.toAbsolutePath() + (accountDir != null ? " and " + accountDir.toAbsolutePath() : ""));
        return 0;
    }

    private static int list(String profile, String account, String[] args) throws Exception {
        boolean all = false;
        boolean json = false;
        Path out = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--all".equalsIgnoreCase(a)) all = true;
            else if ("--json".equalsIgnoreCase(a)) json = true;
            else if ("--out".equalsIgnoreCase(a) && i + 1 < args.length) out = Path.of(args[++i]);
        }

        FileConfigService cfg = FileConfigService.defaultFromWorkingDir(profile, account);

        List<String> lines = new ArrayList<>();
        for (ConfigKey k : ConfigKey.values()) {
            String v = cfg.get(k.key(), "");
            if (!all && (v == null || v.isBlank())) continue;
            lines.add(k.key() + "=" + ConfigPrinter.maskValue(v));
        }
        lines.add("chatGptApiUrl=" + ConfigPrinter.maskValue(cfg.get("chatGptApiUrl", "")));

        if (json) {
            Map<String, String> kv = new LinkedHashMap<>();
            for (String ln : lines) {
                int idx = ln.indexOf('=');
                kv.put(ln.substring(0, idx), ln.substring(idx + 1));
            }
            writeOut(out, toJson(profile, account, kv));
            return 0;
        }

        System.out.println("=== Quantor Keys" + (profile == null ? "" : " [profile=" + profile + "]") + (account == null ? "" : " [account=" + account + "]") + " ===");
        for (String ln : lines) System.out.println(ln);
        return 0;
    }

    private static int get(String profile, String account, String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: configure get <KEY> [--json] [--out <path>]");
            return 1;
        }
        String key = args[0];
        boolean json = false;
        Path out = null;
        for (int i = 1; i < args.length; i++) {
            if ("--json".equalsIgnoreCase(args[i])) json = true;
            else if ("--out".equalsIgnoreCase(args[i]) && i + 1 < args.length) out = Path.of(args[++i]);
        }

        FileConfigService cfg = FileConfigService.defaultFromWorkingDir(profile, account);
        String val = ConfigPrinter.maskValue(cfg.get(key, ""));

        if (json) {
            String payload = "{\"ok\":true,\"profile\":" + str(profile) + ",\"account\":" + str(account) + ",\"key\":" + str(key) + ",\"value\":" + str(val) + "}";
            writeOut(out, payload);
            return 0;
        }

        System.out.println(key + "=" + val);
        return 0;
    }

    private static int set(String profile, String account, String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: configure set <KEY> <VALUE> [--secret] [--encrypt] [--json] [--out <path>]");
            return 1;
        }
        String key = args[0];
        String value = args[1];
        boolean secret = false;
        boolean encrypt = false;
        boolean json = false;
        Path out = null;

        for (int i = 2; i < args.length; i++) {
            String a = args[i];
            if ("--secret".equalsIgnoreCase(a)) secret = true;
            else if ("--encrypt".equalsIgnoreCase(a)) { secret = true; encrypt = true; }
            else if ("--json".equalsIgnoreCase(a)) json = true;
            else if ("--out".equalsIgnoreCase(a) && i + 1 < args.length) out = Path.of(args[++i]);
        }

        Path profileDir = resolveProfileDir(profile);
        Path accountDir = resolveAccountDir(profile, account);

        Path target = (secret ? profileDir.resolve("secrets.properties") : profileDir.resolve("config.properties"));
        if (accountDir != null && secret) {
            target = accountDir.resolve("secrets.properties");
        }

        Properties props = new Properties();
        if (Files.exists(target)) {
            try (var in = Files.newInputStream(target)) { props.load(in); }
        }

        String stored = value;
        if (encrypt) {
            String pwd = System.getenv("QUANTOR_MASTER_PASSWORD");
            if (pwd == null || pwd.isBlank()) pwd = System.getProperty("quantor.masterPassword");
            if (pwd == null || pwd.isBlank()) {
                System.out.println("❌ QUANTOR_MASTER_PASSWORD is required for --encrypt");
                return 1;
            }
            char[] masterPassword = pwd.toCharArray();
            // AesGcmCrypto constructor is private; use its static helper.
            stored = "ENC(" + AesGcmCrypto.encryptToBase64(value, masterPassword) + ")";
        }

        props.setProperty(key, stored);
        try (var outStream = Files.newOutputStream(target)) { props.store(outStream, "Quantor"); }

        // audit
        AuditUtil.appendAudit(profileDir, "SET", key, target);

        String masked = ConfigPrinter.maskValue(stored);
        if (json) {
            String payload = "{\"ok\":true,\"profile\":" + str(profile) + ",\"account\":" + str(account) + ",\"key\":" + str(key) + ",\"value\":" + str(masked) + ",\"file\":" + str(target.toString()) + "}";
            writeOut(out, payload);
            return 0;
        }

        System.out.println("✅ Set " + key + "=" + masked + " in " + target);
        return 0;
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar quantor-cli.jar configure [--profile <name>] [--account <name>] [--non-interactive] <subcommand>");
        System.out.println("Subcommands: init, list, get, set, export");
    }

  private static char[] readPassword(String prompt) {
    try {
      java.io.Console c = System.console();
      if (c != null) {
        char[] p = c.readPassword("%s", prompt);
        return p != null ? p : new char[0];
      }
    } catch (Exception ignored) {}
    System.out.print(prompt);
    java.util.Scanner sc = new java.util.Scanner(System.in);
    String s = sc.nextLine();
    return s != null ? s.toCharArray() : new char[0];
  }

}
