package com.quantor.cli.tools;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Setup wizard.
 *
 * Interactive default:
 *   java -jar quantor-cli.jar setup [--profile <name>] [--account <name>]
 *
 * Interactive from TUI (recommended):
 *   SetupWizard.run(args, lineReader)
 *
 * CI / non-interactive:
 *   java -jar quantor-cli.jar setup --non-interactive --from-env [--profile <name>] [--account <name>]
 *
 * Environment variables for --from-env:
 *   QUANTOR_MASTER_PASSWORD (optional; required to encrypt)
 *   QUANTOR_BINANCE_API_KEY
 *   QUANTOR_BINANCE_API_SECRET
 *   QUANTOR_TELEGRAM_BOT_TOKEN
 *   QUANTOR_TELEGRAM_CHAT_ID
 *   QUANTOR_CHATGPT_API_KEY
 *   QUANTOR_CHATGPT_API_URL (or chatGptApiUrl in config)
 */
public final class SetupWizard {

    private SetupWizard() {}

    // special token: user chose "back"
    private static final String BACK = "__BACK__";

    // ===== Public entrypoints =====

    /**
     * CLI entrypoint (works fine when you run: java -jar ... setup)
     * Uses System console via JLine owned by JVM terminal (works ok standalone).
     *
     * If you call setup from within QuantorMenuV2, use the overload with LineReader.
     */
    public static int run(String[] args) {
        // Standalone CLI mode: create a temporary LineReader via JLine terminal from ConfigureTool (simpler)
        // But to keep this file self-contained and avoid extra TerminalBuilder here,
        // we will fallback to non-interactive if requested; otherwise we will use a very simple stdin flow:
        // -> Better: call run(args, lr) from menu.
        try {
            // In your project you already had a Scanner-based version.
            // To avoid broken input in raw-mode, prefer calling run(args, lr) from TUI menu.
            return run(args, null);
        } catch (Exception e) {
            System.out.println("❌ setup failed: " + e.getMessage());
            return 1;
        }
    }

    /**
     * TUI-friendly entrypoint: uses provided LineReader (backspace works, arrows work).
     */
    public static int run(String[] args, LineReader lr) {
        String profile = null;
        String account = null;
        boolean nonInteractive = false;
        boolean fromEnv = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--profile".equalsIgnoreCase(a) && i + 1 < args.length) profile = args[++i];
            else if ("--account".equalsIgnoreCase(a) && i + 1 < args.length) account = args[++i];
            else if ("--non-interactive".equalsIgnoreCase(a)) nonInteractive = true;
            else if ("--from-env".equalsIgnoreCase(a)) fromEnv = true;
        }

        try {
            // Ensure templates exist
            ConfigureTool.run(buildArgs(profile, account, "init"));

            if (nonInteractive) {
                if (!fromEnv) {
                    System.out.println("❌ setup --non-interactive requires --from-env");
                    return 2;
                }
                return applyFromEnv(profile, account);
            }

            if (lr == null) {
                // If no LineReader provided, we still can do interactive using a minimal approach:
                // ask user to run via menu (recommended).
                System.out.println("ℹ️ SetupWizard: interactive mode requires LineReader (run from menu).");
                System.out.println("   Use: java -jar quantor-cli.jar menu  -> Setup wizard");
                return 3;
            }

            return interactiveJline(profile, account, lr);

        } catch (Exception e) {
            System.out.println("❌ setup failed: " + e.getMessage());
            return 1;
        }
    }

    // ===== Non-interactive =====

    private static int applyFromEnv(String profile, String account) throws Exception {
        Map<String, String> env = System.getenv();

        boolean canEncrypt = env.get("QUANTOR_MASTER_PASSWORD") != null
                && !env.get("QUANTOR_MASTER_PASSWORD").isBlank();

        String[][] mapping = new String[][]{
                {"BINANCE_API_KEY", "QUANTOR_BINANCE_API_KEY"},
                {"BINANCE_API_SECRET", "QUANTOR_BINANCE_API_SECRET"},
                {"TELEGRAM_BOT_TOKEN", "QUANTOR_TELEGRAM_BOT_TOKEN"},
                {"TELEGRAM_CHAT_ID", "QUANTOR_TELEGRAM_CHAT_ID"},
                {"CHATGPT_API_KEY", "QUANTOR_CHATGPT_API_KEY"},
                {"chatGptApiUrl", "QUANTOR_CHATGPT_API_URL"}
        };

        for (String[] m : mapping) {
            String key = m[0];
            String ev = m[1];
            String val = env.get(ev);
            if (val == null) continue;

            List<String> args = new ArrayList<>();
            if (profile != null) { args.add("--profile"); args.add(profile); }
            if (account != null) { args.add("--account"); args.add(account); }
            args.add("set");
            args.add(key);
            args.add(val);
            args.add("--secret");
            if (canEncrypt && !"chatGptApiUrl".equals(key)) args.add("--encrypt");
            args.add("--json");
            ConfigureTool.run(args.toArray(new String[0]));
        }

        // Validate
        List<String> v = new ArrayList<>();
        if (profile != null) { v.add("--profile"); v.add(profile); }
        if (account != null) { v.add("--account"); v.add(account); }
        v.add("--json");
        return ConfigDoctor.run(v.toArray(new String[0]));
    }

    // ===== Interactive (JLine) with BACK =====

    private static int interactiveJline(String profile, String account, LineReader lr) throws Exception {
        System.out.println("=== Quantor Setup"
                + (profile == null ? "" : " [profile=" + profile + "]")
                + (account == null ? "" : " [account=" + account + "]")
                + " ===");
        System.out.println("This will create config files and set secrets (optionally encrypted).");
        System.out.println("Tip: type 'b' or 'back' anytime to go to previous step, 'q' to cancel.");
        System.out.println("");

        String binKey = "";
        String binSecret = "";
        String tTok = "";
        String tChat = "";
        String gptKey = "";
        String gptUrl = "";
        boolean encrypt = false;

        int step = 0;

        while (true) {
            switch (step) {
                case 0 -> {
                    String v = readSecretWithBack(lr, "BINANCE_API_KEY: ");
                    if (isCancel(v)) return 4;
                    if (isBack(v)) { step = Math.max(0, step - 1); break; }
                    binKey = v.trim();
                    step++;
                }
                case 1 -> {
                    String v = readSecretWithBack(lr, "BINANCE_API_SECRET: ");
                    if (isCancel(v)) return 4;
                    if (isBack(v)) { step--; break; }
                    binSecret = v.trim();
                    step++;
                }
                case 2 -> {
                    String v = readSecretWithBack(lr, "TELEGRAM_BOT_TOKEN: ");
                    if (isCancel(v)) return 4;
                    if (isBack(v)) { step--; break; }
                    tTok = v.trim();
                    step++;
                }
                case 3 -> {
                    String v = readLineWithBack(lr, "TELEGRAM_CHAT_ID: ");
                    if (isCancel(v)) return 4;
                    if (isBack(v)) { step--; break; }
                    tChat = v.trim();
                    step++;
                }
                case 4 -> {
                    String v = readSecretWithBack(lr, "CHATGPT_API_KEY: ");
                    if (isCancel(v)) return 4;
                    if (isBack(v)) { step--; break; }
                    gptKey = v.trim();
                    step++;
                }
                case 5 -> {
                    String v = readLineWithBack(lr, "chatGptApiUrl (optional): ");
                    if (isCancel(v)) return 4;
                    if (isBack(v)) { step--; break; }
                    gptUrl = v.trim();
                    step++;
                }
                case 6 -> {
                    String v = readLineWithBack(lr, "Encrypt secrets? (y/N): ");
                    if (isCancel(v)) return 4;
                    if (isBack(v)) { step--; break; }
                    encrypt = "y".equalsIgnoreCase(v.trim());
                    step++;
                }
                default -> {
                    // apply
                    if (encrypt) {
                        System.out.println("ℹ️ Encryption requires QUANTOR_MASTER_PASSWORD in your environment.");
                    }

                    setSecret(profile, account, "BINANCE_API_KEY", binKey, encrypt);
                    setSecret(profile, account, "BINANCE_API_SECRET", binSecret, encrypt);
                    setSecret(profile, account, "TELEGRAM_BOT_TOKEN", tTok, encrypt);
                    setSecret(profile, account, "TELEGRAM_CHAT_ID", tChat, encrypt);
                    setSecret(profile, account, "CHATGPT_API_KEY", gptKey, encrypt);
                    if (!gptUrl.isBlank()) setSecret(profile, account, "chatGptApiUrl", gptUrl, false);

                    List<String> v = new ArrayList<>();
                    if (profile != null) { v.add("--profile"); v.add(profile); }
                    if (account != null) { v.add("--account"); v.add(account); }
                    v.add("--full");
                    return ConfigDoctor.run(v.toArray(new String[0]));
                }
            }
        }
    }

    private static String readSecretWithBack(LineReader lr, String prompt) {
        try {
            String s = lr.readLine(prompt, '*');
            if (s == null) return "";
            s = s.trim();
            if (isBackWord(s)) return BACK;
            return s;
        } catch (UserInterruptException | EndOfFileException e) {
            return "q";
        }
    }

    private static String readLineWithBack(LineReader lr, String prompt) {
        try {
            String s = lr.readLine(prompt);
            if (s == null) return "";
            s = s.trim();
            if (isBackWord(s)) return BACK;
            return s;
        } catch (UserInterruptException | EndOfFileException e) {
            return "q";
        }
    }

    private static boolean isBackWord(String s) {
        if (s == null) return false;
        String x = s.trim().toLowerCase();
        return x.equals("b") || x.equals("back") || x.equals("назад") || x.equals("<<");
    }

    private static boolean isBack(String s) {
        return BACK.equals(s);
    }

    private static boolean isCancel(String s) {
        if (s == null) return false;
        String x = s.trim().toLowerCase();
        return x.equals("q") || x.equals("quit") || x.equals("exit") || x.equals("cancel");
    }

    // ===== Shared helpers =====

    private static String[] buildArgs(String profile, String account, String sub) {
        List<String> a = new ArrayList<>();
        if (profile != null) { a.add("--profile"); a.add(profile); }
        if (account != null) { a.add("--account"); a.add(account); }
        a.add(sub);
        return a.toArray(new String[0]);
    }

    private static void setSecret(String profile, String account, String key, String value, boolean encrypt) throws Exception {
        if (value == null || value.isBlank()) return;

        List<String> args = new ArrayList<>();
        if (profile != null) { args.add("--profile"); args.add(profile); }
        if (account != null) { args.add("--account"); args.add(account); }
        args.add("set");
        args.add(key);
        args.add(value);
        args.add("--secret");
        if (encrypt && !"chatGptApiUrl".equals(key)) args.add("--encrypt");
        ConfigureTool.run(args.toArray(new String[0]));
    }
}
