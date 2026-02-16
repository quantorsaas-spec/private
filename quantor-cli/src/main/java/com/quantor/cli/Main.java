package com.quantor.cli;

import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.exchange.ExchangeId;
import com.quantor.application.exchange.MarketSymbol;
import com.quantor.application.exchange.Timeframe;
import com.quantor.application.exchange.Timeframes;
import com.quantor.application.ports.ConfigPort;
import com.quantor.application.service.SessionService;
import com.quantor.cli.bootstrap.Bootstrap;
import com.quantor.cli.tools.ConfigDoctor;
import com.quantor.cli.tools.ConfigureTool;
import com.quantor.cli.tools.PreflightTool;
import com.quantor.cli.tools.SecretsEncryptor;
import com.quantor.cli.tools.SetupWizard;
import com.quantor.cli.tui.QuantorMenuV2;
import com.quantor.infrastructure.config.FileConfigService;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws Exception {

        // Default: no args => open TUI menu
        if (args.length == 0) {
            System.exit(QuantorMenuV2.run());
            return;
        }

        // Commands
        String cmd = args[0].trim().toLowerCase();
        String[] tail = Arrays.copyOfRange(args, 1, args.length);

        switch (cmd) {
            case "menu":
            case "tui":
            case "menu2":
                System.exit(QuantorMenuV2.run());
                return;

            case "setup":
                System.exit(SetupWizard.run(tail));
                return;

            case "encrypt-secrets":
                System.exit(SecretsEncryptor.run());
                return;

            case "validate-config":
                System.exit(ConfigDoctor.run(tail));
                return;

            case "doctor": {
                boolean hasFull = false;
                for (String t : tail) {
                    if ("--full".equalsIgnoreCase(t)) {
                        hasFull = true;
                        break;
                    }
                }
                if (!hasFull) {
                    String[] withFull = new String[tail.length + 1];
                    withFull[0] = "--full";
                    System.arraycopy(tail, 0, withFull, 1, tail.length);
                    System.exit(ConfigDoctor.run(withFull));
                } else {
                    System.exit(ConfigDoctor.run(tail));
                }
                return;
            }

            case "configure":
                System.exit(ConfigureTool.run(tail));
                return;

            case "preflight":
                System.exit(PreflightTool.run(tail));
                return;

            // STOP-FIX: Telegram mode MUST NOT auto-start sessions from CLI.
            case "telegram":
                System.exit(TelegramRunner.run(tail));
                return;

            case "help":
            case "--help":
            case "-h":
                printHelp();
                return;

            case "run": {
                RunOptions ro = parseRunOptions(tail, 0);
                startEngine(ro.symbol, ro.interval, ro.lookback, ro.overrides);
                return;
            }

            default:
                // Not a known command -> treat as engine args
                break;
        }

        // Engine mode: <symbol> <interval> <lookback> [--exchange X] [--mode TEST|LIVE]
        RunOptions ro = parseRunOptions(args, 0);
        startEngine(ro.symbol, ro.interval, ro.lookback, ro.overrides);
    }

    private static void startEngine(String symbol, String interval, int lookback, Map<String, String> extraOverrides) {
        ConfigPort config;
        try {
            config = FileConfigService.defaultFromWorkingDir();
        } catch (IOException e) {
            System.err.println("Failed to load config from working directory: " + e.getMessage());
            return;
        }

        // Apply overrides (symbol/interval/lookback always win over file config)
        OverrideConfig oc = new OverrideConfig(config)
                .with("trade.symbol", symbol)
                .with("symbol", symbol)
                .with("trade.interval", interval)
                .with("interval", interval)
                .with("trade.lookback", String.valueOf(lookback));

        if (extraOverrides != null) {
            for (Map.Entry<String, String> e : extraOverrides.entrySet()) {
                oc.with(e.getKey(), e.getValue());
            }
        }
        config = oc;

        // STOP-FIX: global gate for CLI "run" mode
        boolean tradingEnabled = Boolean.parseBoolean(config.get("trading.enabled", "true"));
        if (!tradingEnabled) {
            String reason = config.get("trading.disabledReason", "Trading disabled");
            System.out.println("TRADING DISABLED: " + reason);
            return;
        }

        SessionService sessions = Bootstrap.createSessionService(config);

        // Exchange selection (supports either "exchange" or "trade.exchange" config keys)
        String exRaw = firstNonEmpty(config.get("trade.exchange", ""), config.get("exchange", "BINANCE"));
        ExchangeId exchangeId = ExchangeId.valueOf(exRaw.trim().toUpperCase());

        ExchangeId marketDataExchange = ExchangeId.valueOf(config.get("paper.marketDataExchange", "BINANCE").trim().toUpperCase());
        if (exchangeId != ExchangeId.PAPER) {
            marketDataExchange = exchangeId;
        }

        MarketSymbol ms = MarketSymbol.parse(symbol);
        Timeframe tf = Timeframes.parse(interval);
        ExecutionJob job = new ExecutionJob(
                "local",
                config.get("strategyType", "online"),
                exchangeId,
                marketDataExchange,
                ms,
                tf,
                lookback
        );

        long periodMs = timeframeToMs(tf);
        System.out.println("[Quantor] Starting session: " + job.key() + " periodMs=" + periodMs);
        sessions.start(job, periodMs);

        System.out.println("[Quantor] Running. Press ENTER to stop.");
        try {
            while (System.in.read() != '\n') {
                // ignore
            }
        } catch (Exception ignore) {}

        sessions.stop(job);
        System.out.println("[Quantor] Stopped.");
    }

    private static long timeframeToMs(Timeframe tf) {
        if (tf == null) return 60_000L;
        return switch (tf) {
            case M1 -> 60_000L;
            case M3 -> 3 * 60_000L;
            case M5 -> 5 * 60_000L;
            case M15 -> 15 * 60_000L;
            case M30 -> 30 * 60_000L;
            case H1 -> 3_600_000L;
            case H4 -> 4 * 3_600_000L;
            case D1 -> 86_400_000L;
        };
    }

    private static int safeParseInt(String s, int def) {
        if (s == null || s.trim().isEmpty()) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a;
        return b;
    }

    private static final class RunOptions {
        final String symbol;
        final String interval;
        final int lookback;
        final Map<String, String> overrides;

        private RunOptions(String symbol, String interval, int lookback, Map<String, String> overrides) {
            this.symbol = symbol;
            this.interval = interval;
            this.lookback = lookback;
            this.overrides = overrides;
        }
    }

    /**
     * Parses either:
     *  - <symbol> <interval> <lookback> [--exchange X] [--mode TEST|LIVE]
     *  - --symbol S --interval I --lookback N --exchange X ...
     */
    private static RunOptions parseRunOptions(String[] args, int startIndex) {
        String symbol = null;
        String interval = null;
        Integer lookback = null;
        Map<String, String> overrides = new HashMap<>();

        int i = startIndex;
        // positionals first (if present)
        if (i < args.length && !args[i].startsWith("--")) {
            symbol = args[i++];
        }
        if (i < args.length && !args[i].startsWith("--")) {
            interval = args[i++];
        }
        if (i < args.length && !args[i].startsWith("--")) {
            lookback = safeParseInt(args[i++], 200);
        }

        while (i < args.length) {
            String t = args[i];
            if (t == null) {
                i++;
                continue;
            }
            t = t.trim();
            if (t.isEmpty()) {
                i++;
                continue;
            }

            if ("--symbol".equalsIgnoreCase(t) && i + 1 < args.length) {
                symbol = args[i + 1];
                i += 2;
                continue;
            }
            if ("--interval".equalsIgnoreCase(t) && i + 1 < args.length) {
                interval = args[i + 1];
                i += 2;
                continue;
            }
            if (("--lookback".equalsIgnoreCase(t) || "--lb".equalsIgnoreCase(t)) && i + 1 < args.length) {
                lookback = safeParseInt(args[i + 1], 200);
                i += 2;
                continue;
            }
            if ("--exchange".equalsIgnoreCase(t) && i + 1 < args.length) {
                String ex = args[i + 1];
                // support both keys (bootstrap checks both)
                overrides.put("trade.exchange", ex);
                overrides.put("exchange", ex);
                i += 2;
                continue;
            }
            if ("--mode".equalsIgnoreCase(t) && i + 1 < args.length) {
                overrides.put("mode", args[i + 1]);
                i += 2;
                continue;
            }

            // ignore unknown flags
            i++;
        }

        if (symbol == null || symbol.trim().isEmpty()) symbol = "BTCUSDT";
        if (interval == null || interval.trim().isEmpty()) interval = "1m";
        if (lookback == null) lookback = 200;

        return new RunOptions(symbol, interval, lookback, overrides);
    }

    private static void printHelp() {
        System.out.println("Quantor CLI");
        System.out.println("Usage:");
        System.out.println("  java -jar quantor-cli.jar               (opens TUI menu)");
        System.out.println("  java -jar quantor-cli.jar menu|tui|menu2");
        System.out.println("  java -jar quantor-cli.jar setup");
        System.out.println("  java -jar quantor-cli.jar configure init");
        System.out.println("  java -jar quantor-cli.jar encrypt-secrets");
        System.out.println("  java -jar quantor-cli.jar validate-config");
        System.out.println("  java -jar quantor-cli.jar doctor");
        System.out.println("  java -jar quantor-cli.jar preflight");
        System.out.println("  java -jar quantor-cli.jar telegram             (run Telegram command bot)");
        System.out.println("  java -jar quantor-cli.jar <symbol> <interval> <lookback> [--exchange X] [--mode TEST|LIVE]");
        System.out.println("  java -jar quantor-cli.jar run --symbol BTC-USD --interval 1m --lookback 200 --exchange COINBASE");
    }

    private static final class OverrideConfig implements ConfigPort {
        private final ConfigPort delegate;
        private final Map<String, String> overrides = new HashMap<>();

        private OverrideConfig(ConfigPort delegate) {
            this.delegate = delegate;
        }

        private OverrideConfig with(String key, String value) {
            if (key != null && value != null) overrides.put(key, value);
            return this;
        }

        @Override
        public String get(String key) {
            String v = overrides.get(key);
            return v != null ? v : delegate.get(key);
        }

        @Override
        public String get(String key, String defaultValue) {
            return overrides.getOrDefault(key, delegate.get(key, defaultValue));
        }

        @Override
        public int getInt(String key, int defaultValue) {
            String v = overrides.get(key);
            if (v == null) return delegate.getInt(key, defaultValue);
            try {
                return Integer.parseInt(v.trim());
            } catch (Exception e) {
                return defaultValue;
            }
        }

        @Override
        public double getDouble(String key, double defaultValue) {
            String v = overrides.get(key);
            if (v == null) return delegate.getDouble(key, defaultValue);
            try {
                return Double.parseDouble(v.trim());
            } catch (Exception e) {
                return defaultValue;
            }
        }

        @Override
        public String getSecret(String key) {
            return delegate.getSecret(key);
        }
    }
}
