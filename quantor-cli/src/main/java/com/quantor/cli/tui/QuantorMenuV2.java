package com.quantor.cli.tui;

import com.quantor.application.engine.LiveEngine;
import com.quantor.application.ports.ConfigPort;
import com.quantor.cli.bootstrap.Bootstrap;
import com.quantor.cli.tools.*;
import com.quantor.infrastructure.config.FileConfigService;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.jline.utils.NonBlockingReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class QuantorMenuV2 {

    private QuantorMenuV2() {}

    // ======= Quantor theme (ANSI) =======
    private static final String RESET = "\u001B[0m";
    private static final String DIM   = "\u001B[2m";
    private static final String BOLD  = "\u001B[1m";

    private static final String FG_CYAN   = "\u001B[36m";
    private static final String FG_BLUE   = "\u001B[34m";
    private static final String FG_GREEN  = "\u001B[32m";
    private static final String FG_YELLOW = "\u001B[33m";
    private static final String FG_RED    = "\u001B[31m";
    private static final String FG_WHITE  = "\u001B[37m";

    // background highlight for selection
    private static final String BG_SEL = "\u001B[48;5;236m"; // dark gray
    private static final String FG_SEL = "\u001B[38;5;45m";  // bright cyan-ish

    private static final class Ctx {
        String profile = "";   // "" = default (./config)
        String account = "";   // "" = default
        String symbol = "BTCUSDT";
        String interval = "1m";
        int lookback = 200;
        boolean testMode = true;

        LiveEngine runningEngine;
        Thread runningThread;
    }

    public static int run() {
        Ctx ctx = new Ctx();

        try (Terminal terminal = buildTerminal()) {

            // Save original terminal attributes (IMPORTANT for cooked tools / typing)
            final Attributes originalAttr = terminal.getAttributes();

            // Raw mode for menu navigation
            terminal.enterRawMode();
            terminal.echo(false);

            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();

            List<MenuItem> items = List.of(
                    // ✅ FIX: SetupWizard uses JLine from menu (backspace works)
                    new MenuItem("1", "🧩 Setup wizard", "создать config + секреты",
                            () -> runCooked(terminal, originalAttr, () -> SetupWizard.run(new String[0], lineReader))),

                    new MenuItem("2", "🔐 Encrypt secrets", "secrets.properties → secrets.enc",
                            () -> runCooked(terminal, originalAttr, SecretsEncryptor::run)),

                    new MenuItem("3", "🩺 Validate config", "doctor --full",
                            () -> runCooked(terminal, originalAttr, () -> runDoctor(ctx))),

                    new MenuItem("4", "🌐 Preflight checks", "Binance / Telegram / ChatGPT",
                            () -> runCooked(terminal, originalAttr, () -> PreflightTool.run(preflightArgs(ctx)))),

                    new MenuItem("5", "⚙️ Configure tool", "init/list/get/set",
                            () -> runCooked(terminal, originalAttr, () -> ConfigureTool.run(new String[]{"init"}))),

                    new MenuItem("6", "📁 Profile / Account", "выбрать папки", () -> {
                        withEcho(terminal, true, () -> profileAccountForm(terminal, lineReader, ctx));
                        return 0;
                    }),

                    new MenuItem("7", "📈 Trade params", "symbol / interval / lookback", () -> {
                        withEcho(terminal, true, () -> tradeParamsForm(terminal, lineReader, ctx));
                        return 0;
                    }),

                    new MenuItem("8", "🌓 Toggle mode", "TEST ↔ LIVE", () -> {
                        ctx.testMode = !ctx.testMode;
                        toast(terminal, "Mode switched to: " + (ctx.testMode ? "TEST" : "LIVE"),
                                ctx.testMode ? FG_YELLOW : FG_RED);
                        waitAnyKey(terminal, "Press any key to return…");
                        return 0;
                    }),

                    new MenuItem("9", "📋 Status", "проверка файлов/выборов", () -> {
                        statusScreen(terminal, ctx);
                        waitAnyKey(terminal, "Press any key to return…");
                        return 0;
                    }),

                    new MenuItem("0", "▶ Run engine NOW", "запуск из меню", () -> {
                        int rc = runEngineNow(terminal, ctx);
                        waitAnyKey(terminal, "Press any key to return…");
                        return rc;
                    }),

                    new MenuItem("S", "⛔ Stop engine", "если запущен", () -> {
                        stopEngineIfRunning(terminal, ctx);
                        waitAnyKey(terminal, "Press any key to return…");
                        return 0;
                    }),

                    new MenuItem("Esc", "🚪 Exit", "выход", () -> 0)
            );

            int idx = 0;

            while (true) {
                idx = menuLoop(terminal, ctx, items, idx);

                // exit chosen
                if (idx == items.size() - 1) {
                    stopEngineIfRunning(terminal, ctx);
                    clear(terminal);
                    println(terminal, FG_CYAN + "Bye!" + RESET);
                    return 0;
                }

                // run action
                clear(terminal);
                items.get(idx).action.run();
            }

        } catch (Exception e) {
            System.err.println("[QuantorMenuV2] Error: " + e.getMessage());
            return 20;
        }
    }

    /**
     * Windows terminals can be flaky with JNA/JANSI depending on the host (PowerShell vs Windows Terminal vs ConHost).
     * We try a few safe fallbacks instead of crashing the whole CLI.
     */
    private static Terminal buildTerminal() throws Exception {
        // 1) Best UX (colors + proper key handling)
        try {
            return TerminalBuilder.builder()
                    .system(true)
                    .jna(true)
                    .jansi(true)
                    .build();
        } catch (Throwable ignored) {
            // continue
        }

        // 2) Disable JNA (common Windows issue), keep JANSI for colors
        try {
            return TerminalBuilder.builder()
                    .system(true)
                    .jna(false)
                    .jansi(true)
                    .build();
        } catch (Throwable ignored) {
            // continue
        }

        // 3) Most compatible fallback
        return TerminalBuilder.builder()
                .system(true)
                .jna(false)
                .jansi(false)
                .dumb(true)
                .build();
    }

    // ===== Menu core =====

    private static int menuLoop(Terminal t, Ctx ctx, List<MenuItem> items, int startIdx) throws Exception {
        NonBlockingReader r = t.reader();
        int idx = clamp(startIdx, 0, items.size() - 1);

        while (true) {
            drawMenu(t, ctx, items, idx);

            int ch = r.read(); // blocking
            if (ch < 0) continue;

            // Enter
            if (ch == 10 || ch == 13) return idx;

            // Esc (plain) => exit item
            if (ch == 27) {
                int ch2 = r.read(250);
                if (ch2 == -1) return items.size() - 1; // Exit
                if (ch2 == '[') {
                    // read until A/B (ignore params)
                    for (int i = 0; i < 12; i++) {
                        int ch3 = r.read(120);
                        if (ch3 == -1) break;
                        if (ch3 == 'A') { idx = (idx - 1 + items.size()) % items.size(); break; }
                        if (ch3 == 'B') { idx = (idx + 1) % items.size(); break; }
                    }
                }
                continue;
            }

            // arrows can come as ESC O A/B
            if (ch == 'O') {
                int ch2 = r.read(120);
                if (ch2 == 'A') idx = (idx - 1 + items.size()) % items.size();
                if (ch2 == 'B') idx = (idx + 1) % items.size();
                continue;
            }

            // vim keys
            if (ch == 'j' || ch == 'J') { idx = (idx + 1) % items.size(); continue; }
            if (ch == 'k' || ch == 'K') { idx = (idx - 1 + items.size()) % items.size(); continue; }

            // w/s fallback
            if (ch == 's' || ch == 'S') { idx = (idx + 1) % items.size(); continue; }
            if (ch == 'w' || ch == 'W') { idx = (idx - 1 + items.size()) % items.size(); continue; }

            // hotkeys: digits + S/s
            if (ch >= '0' && ch <= '9') {
                String key = String.valueOf((char) ch);
                int hit = findByKey(items, key);
                if (hit >= 0) return hit;
            }
            if (ch == 'S' || ch == 's') {
                int hit = findByKey(items, "S");
                if (hit >= 0) return hit;
            }
        }
    }

    private static void drawMenu(Terminal t, Ctx ctx, List<MenuItem> items, int idx) {
        clear(t);

        // Header
        println(t, quantorHeader(ctx));

        // Frame top
        int width = 74;
        println(t, boxTop(width, " QUANTOR CLI "));

        for (int i = 0; i < items.size(); i++) {
            MenuItem it = items.get(i);

            String left = " [" + it.key + "] ";
            String title = it.title;
            String hint = it.hint;

            String line = left + title;
            String rhs = DIM + hint + RESET;

            String padded = padRight(line, width - 2 - visibleLen(rhs) - 1) + " " + rhs;

            if (i == idx) {
                println(t, "│" + BG_SEL + FG_SEL + " " + padded + " " + RESET + "│");
            } else {
                println(t, "│ " + padded + " │");
            }
        }

        // Frame bottom + footer
        println(t, boxBottom(width));
        println(t, footerLine());
    }

    private static String quantorHeader(Ctx ctx) {
        String mode = ctx.testMode ? FG_YELLOW + "TEST" + RESET : FG_RED + "LIVE" + RESET;
        String engine = (ctx.runningThread != null && ctx.runningThread.isAlive())
                ? FG_GREEN + "RUNNING" + RESET
                : DIM + "stopped" + RESET;

        String p = display(ctx.profile);
        String a = display(ctx.account);

        String line1 =
                BOLD + FG_CYAN + "   ____                  _             " + RESET + "\n" +
                        BOLD + FG_CYAN + "  / __ \\__  ______ _____ ( )_____  _____" + RESET + "\n" +
                        BOLD + FG_CYAN + " / / / / / / / __ `/ __ \\|// ___/ / ___/" + RESET + "\n" +
                        BOLD + FG_CYAN + "/ /_/ / /_/ / /_/ / / / / (__  ) (__  ) " + RESET + "\n" +
                        BOLD + FG_CYAN + "\\___\\_\\__,_/\\__,_/_/ /_/ /____/ /____/  " + RESET;

        String line2 = String.format(
                "%s%sprofile%s=%s  %saccount%s=%s  %smode%s=%s  %strade%s=%s/%s lb=%d  %sengine%s=%s%s",
                DIM, FG_WHITE, RESET, FG_BLUE + p + RESET,
                DIM, FG_WHITE, RESET, FG_BLUE + a + RESET,
                DIM, FG_WHITE, RESET, mode,
                DIM, FG_WHITE, RESET, FG_GREEN + ctx.symbol + RESET, FG_GREEN + ctx.interval + RESET, ctx.lookback,
                DIM, FG_WHITE, RESET, engine,
                RESET
        );

        return line1 + "\n" + line2 + "\n";
    }

    private static String footerLine() {
        String controls = DIM + "Controls: ↑/↓, j/k, 1..9, 0, S, Enter, Esc" + RESET;
        String tip = DIM + "Tip: Setup wizard supports backspace/typing now." + RESET;
        return controls + "\n" + tip;
    }

    // ======= Cooked runner =======

    /**
     * Runs tool that expects normal console mode (System.in).
     * We restore terminal attributes + echo, run the tool, then come back to raw-mode menu.
     */
    private static int runCooked(Terminal t, Attributes originalAttr, IntSupplierEx action) {
        Attributes before = t.getAttributes();
        boolean beforeEcho = t.echo();

        try {
            t.setAttributes(originalAttr);
            t.echo(true);
            t.flush();

            println(t, "");
            return action.run();

        } catch (Exception e) {
            System.err.println("[QuantorMenuV2] Tool error: " + e.getMessage());
            return 30;
        } finally {
            try {
                t.setAttributes(before);
                t.echo(false);
                t.flush();
            } catch (Exception ignored) {}

            try {
                t.echo(beforeEcho);
            } catch (Exception ignored) {}
        }
    }

    private static void withEcho(Terminal t, boolean on, Runnable r) {
        boolean before = t.echo();
        try {
            t.echo(on);
            r.run();
        } finally {
            t.echo(before);
        }
    }

    // ======= Actions =======

    private static int runDoctor(Ctx ctx) {
        if (ctx.profile == null || ctx.profile.isBlank()) {
            return ConfigDoctor.run(new String[]{"--full"});
        }
        return ConfigDoctor.run(new String[]{"--full", "--profile", ctx.profile.trim()});
    }

    private static void profileAccountForm(Terminal t, LineReader lr, Ctx ctx) {
        clear(t);
        println(t, BOLD + FG_CYAN + "Profile / Account" + RESET);
        println(t, DIM + "Empty = default. Finish input with Enter." + RESET);
        println(t, "");

        println(t, "Profile folder: ./config/<profile>/");
        String p = lr.readLine("Profile [" + ctx.profile + "]: ").trim();
        if (!p.isEmpty()) ctx.profile = p;

        println(t, "");
        println(t, "Account folder: ./config/<profile>/accounts/<account>/");
        String a = lr.readLine("Account [" + ctx.account + "]: ").trim();
        if (!a.isEmpty()) ctx.account = a;

        println(t, "");
        toast(t, "Saved: profile=" + display(ctx.profile) + ", account=" + display(ctx.account), FG_GREEN);
        waitAnyKey(t, "Press any key to return…");
    }

    private static void tradeParamsForm(Terminal t, LineReader lr, Ctx ctx) {
        clear(t);
        println(t, BOLD + FG_CYAN + "Trade params" + RESET);
        println(t, "");

        String sym = lr.readLine("Symbol [" + ctx.symbol + "]: ").trim();
        if (!sym.isEmpty()) ctx.symbol = sym;

        String inter = lr.readLine("Interval [" + ctx.interval + "]: ").trim();
        if (!inter.isEmpty()) ctx.interval = inter;

        String lb = lr.readLine("Lookback [" + ctx.lookback + "]: ").trim();
        if (!lb.isEmpty()) {
            try { ctx.lookback = Integer.parseInt(lb); }
            catch (Exception e) { toast(t, "Invalid lookback, keep: " + ctx.lookback, FG_YELLOW); }
        }

        println(t, "");
        toast(t, "Saved: " + ctx.symbol + " " + ctx.interval + " lookback=" + ctx.lookback, FG_GREEN);
        waitAnyKey(t, "Press any key to return…");
    }

    private static void statusScreen(Terminal t, Ctx ctx) {
        clear(t);
        println(t, BOLD + FG_CYAN + "STATUS" + RESET);
        println(t, "");

        println(t, "profile: " + FG_BLUE + display(ctx.profile) + RESET);
        println(t, "account: " + FG_BLUE + display(ctx.account) + RESET);
        println(t, "mode: " + (ctx.testMode ? FG_YELLOW + "TEST" : FG_RED + "LIVE") + RESET);
        println(t, "trade: " + FG_GREEN + ctx.symbol + RESET + " " + FG_GREEN + ctx.interval + RESET + " lookback=" + ctx.lookback);

        Path base = Path.of(System.getProperty("user.dir")).resolve("config");
        if (ctx.profile != null && !ctx.profile.isBlank()) base = base.resolve(ctx.profile.trim());

        Path secretsEnc = base.resolve("secrets.enc");
        Path secretsPlain = base.resolve("secrets.properties");
        Path cfg = base.resolve("config.properties");
        Path env = base.resolve(".env");

        println(t, "");
        println(t, DIM + "configDir: " + RESET + base.toAbsolutePath());
        println(t, " - config.properties: " + exists(cfg));
        println(t, " - .env: " + exists(env));
        println(t, " - secrets.enc: " + exists(secretsEnc));
        println(t, " - secrets.properties: " + exists(secretsPlain));

        if (ctx.account != null && !ctx.account.isBlank()) {
            Path acc = base.resolve("accounts").resolve(ctx.account.trim());
            println(t, "");
            println(t, DIM + "accountDir: " + RESET + acc.toAbsolutePath());
            println(t, " - .env: " + exists(acc.resolve(".env")));
            println(t, " - secrets.properties: " + exists(acc.resolve("secrets.properties")));
        }

        boolean running = ctx.runningThread != null && ctx.runningThread.isAlive();
        println(t, "");
        println(t, "engine: " + (running ? FG_GREEN + "RUNNING" + RESET + " (" + ctx.runningThread.getName() + ")" : DIM + "stopped" + RESET));
    }

    private static int runEngineNow(Terminal t, Ctx ctx) {
        if (ctx.runningThread != null && ctx.runningThread.isAlive()) {
            toast(t, "Engine is already running.", FG_YELLOW);
            return 0;
        }

        try {
            FileConfigService config = FileConfigService.defaultFromWorkingDir(
                    ctx.profile == null || ctx.profile.isBlank() ? null : ctx.profile,
                    ctx.account == null || ctx.account.isBlank() ? null : ctx.account
            );

            ConfigPort finalConfig = new ConfigPort() {
                @Override public String get(String key) {
                    if ("trade.symbol".equals(key)) return ctx.symbol;
                    if ("trade.interval".equals(key)) return ctx.interval;
                    if ("binance.testMode".equals(key)) return String.valueOf(ctx.testMode);
                    return config.get(key);
                }
                @Override public String get(String key, String defaultValue) {
                    String v = get(key);
                    return v == null ? defaultValue : v;
                }
                @Override public int getInt(String key, int defaultValue) {
                    if ("trade.lookback".equals(key)) return ctx.lookback;
                    return config.getInt(key, defaultValue);
                }
                @Override public double getDouble(String key, double defaultValue) {
                    return config.getDouble(key, defaultValue);
                }
                @Override public String getSecret(String key) { return config.getSecret(key); }
            };

            clear(t);
            println(t, BOLD + FG_CYAN + "Starting engine…" + RESET);
            println(t, "Mode: " + (ctx.testMode ? FG_YELLOW + "TEST" : FG_RED + "LIVE") + RESET);
            println(t, "Trade: " + ctx.symbol + " " + ctx.interval + " lookback=" + ctx.lookback);
            println(t, "");

            LiveEngine engine = Bootstrap.createLiveEngine(finalConfig);
            Thread th = new Thread(engine, "quantor-live-engine");
            th.setDaemon(false);
            th.start();

            ctx.runningEngine = engine;
            ctx.runningThread = th;

            toast(t, "Engine started.", FG_GREEN);
            println(t, DIM + "Tip: Telegram commands /pause /resume /stop (if enabled)." + RESET);
            return 0;

        } catch (Exception e) {
            toast(t, "Failed to start engine: " + e.getMessage(), FG_RED);
            return 30;
        }
    }

    private static void stopEngineIfRunning(Terminal t, Ctx ctx) {
        boolean running = ctx.runningThread != null && ctx.runningThread.isAlive();
        if (!running) {
            toast(t, "Engine is not running.", FG_YELLOW);
            return;
        }

        toast(t, "Stopping engine…", FG_YELLOW);
        try {
            if (ctx.runningEngine != null) ctx.runningEngine.stop();
            ctx.runningThread.join(2500);

            if (ctx.runningThread.isAlive()) {
                toast(t, "Still running → interrupt fallback…", FG_YELLOW);
                ctx.runningThread.interrupt();
                ctx.runningThread.join(1500);
            }
        } catch (Exception e) {
            toast(t, "Stop error: " + e.getMessage(), FG_RED);
        } finally {
            ctx.runningThread = null;
            ctx.runningEngine = null;
        }

        toast(t, "Stopped.", FG_GREEN);
    }

    // ======= Helpers =======

    private static void waitAnyKey(Terminal t, String msg) {
        println(t, "");
        println(t, DIM + msg + RESET);
        try { t.reader().read(); } catch (Exception ignored) {}
    }

    private static void toast(Terminal t, String msg, String color) {
        println(t, color + msg + RESET);
    }

    private static void clear(Terminal t) {
        try {
            t.puts(InfoCmp.Capability.clear_screen);
            t.puts(InfoCmp.Capability.cursor_home);
            t.flush();
        } catch (Exception e) {
            t.writer().print("\033[2J\033[H");
            t.writer().flush();
        }
    }

    private static void println(Terminal t, String s) {
        t.writer().println(s);
        t.writer().flush();
    }

    private static String display(String s) {
        return (s == null || s.isBlank()) ? "<default>" : s.trim();
    }

    private static String exists(Path p) {
        return Files.exists(p) ? FG_GREEN + "YES" + RESET : DIM + "no" + RESET;
    }

    private static String[] preflightArgs(Ctx ctx) {
        boolean hasProfile = ctx.profile != null && !ctx.profile.isBlank();
        boolean hasAccount = ctx.account != null && !ctx.account.isBlank();

        if (!hasProfile && !hasAccount) return new String[0];
        if (hasProfile && !hasAccount) return new String[]{"--profile", ctx.profile.trim()};
        if (!hasProfile) return new String[]{"--account", ctx.account.trim()};
        return new String[]{"--profile", ctx.profile.trim(), "--account", ctx.account.trim()};
    }

    private static int findByKey(List<MenuItem> items, String key) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).key.equalsIgnoreCase(key)) return i;
        }
        return -1;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String boxTop(int width, String title) {
        String t = "┌" + "─".repeat(Math.max(0, width - 2)) + "┐";
        String inner = " " + title.trim() + " ";
        int start = 2;
        if (inner.length() < width - 2) {
            int pos = (width - 2 - inner.length()) / 2;
            start = 1 + pos + 1;
        }
        StringBuilder sb = new StringBuilder(t);
        int idx = start;
        for (int i = 0; i < inner.length() && (idx + i) < width - 1; i++) {
            sb.setCharAt(idx + i, inner.charAt(i));
        }
        return FG_BLUE + sb + RESET;
    }

    private static String boxBottom(int width) {
        return FG_BLUE + "└" + "─".repeat(Math.max(0, width - 2)) + "┘" + RESET;
    }

    private static String padRight(String s, int len) {
        int v = visibleLen(s);
        if (v >= len) return s;
        return s + " ".repeat(len - v);
    }

    private static int visibleLen(String s) {
        // naive ANSI strip
        return s.replaceAll("\\u001B\\[[;\\d]*m", "").length();
    }

    // ======= Models =======

    private static final class MenuItem {
        final String key;
        final String title;
        final String hint;
        final IntSupplierEx action;

        MenuItem(String key, String title, String hint, IntSupplierEx action) {
            this.key = key;
            this.title = title;
            this.hint = hint;
            this.action = action;
        }
    }

    @FunctionalInterface
    private interface IntSupplierEx {
        int run() throws Exception;
    }
}
