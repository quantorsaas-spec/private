package com.quantor.cli.menu;

import com.quantor.cli.tools.SecretsEncryptor;

import java.util.Scanner;

public final class MenuApp {

    private MenuApp() {}

    public static int run() {
        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                printHeader();
                printMainMenu();

                String choice = prompt(sc, "Select");
                switch (choice) {
                    case "1" -> {
                        // TODO: вызвать SetupTool (если у тебя он отдельный)
                        System.out.println("[TODO] setup");
                        pause(sc);
                    }
                    case "2" -> secretsMenu(sc);
                    case "3" -> configureMenu(sc);
                    case "4" -> runBotMenu(sc);
                    case "5" -> profileMenu(sc);
                    case "6", "q", "Q", "exit" -> {
                        System.out.println("Bye!");
                        return 0;
                    }
                    default -> {
                        System.out.println("Unknown option: " + choice);
                        pause(sc);
                    }
                }
            }
        }
    }

    private static void secretsMenu(Scanner sc) {
        while (true) {
            System.out.println("\n=== Secrets ===");
            System.out.println("1) Encrypt secrets (create secrets.enc)");
            System.out.println("2) Validate config (doctor)");
            System.out.println("3) Preflight (Binance/Telegram/ChatGPT)");
            System.out.println("4) Back");

            String c = prompt(sc, "Select");
            switch (c) {
                case "1" -> {
                    int code = SecretsEncryptor.run();
                    System.out.println("Exit code: " + code);
                    pause(sc);
                }
                case "2" -> {
                    // TODO: ValidateConfigTool.run()
                    System.out.println("[TODO] validate-config");
                    pause(sc);
                }
                case "3" -> {
                    // TODO: PreflightTool.run()
                    System.out.println("[TODO] preflight");
                    pause(sc);
                }
                case "4" -> { return; }
                default -> System.out.println("Unknown option: " + c);
            }
        }
    }

    private static void configureMenu(Scanner sc) {
        while (true) {
            System.out.println("\n=== Configure ===");
            System.out.println("1) List (masked)");
            System.out.println("2) Get key");
            System.out.println("3) Set key");
            System.out.println("4) Back");

            String c = prompt(sc, "Select");
            switch (c) {
                case "1" -> { System.out.println("[TODO] configure list"); pause(sc); }
                case "2" -> {
                    String key = prompt(sc, "Key");
                    System.out.println("[TODO] configure get " + key);
                    pause(sc);
                }
                case "3" -> {
                    String key = prompt(sc, "Key");
                    String val = prompt(sc, "Value");
                    String enc = prompt(sc, "Encrypt? (y/N)");
                    System.out.println("[TODO] configure set " + key + " " + mask(val) + " encrypt=" + enc);
                    pause(sc);
                }
                case "4" -> { return; }
                default -> System.out.println("Unknown option: " + c);
            }
        }
    }

    private static void runBotMenu(Scanner sc) {
        while (true) {
            System.out.println("\n=== Run bot ===");
            System.out.println("1) Start (custom)");
            System.out.println("2) Preset: BTCUSDT 1m 200");
            System.out.println("3) Preset: ETHUSDT 5m 500");
            System.out.println("4) Back");

            String c = prompt(sc, "Select");
            switch (c) {
                case "1" -> {
                    String symbol = prompt(sc, "Symbol (e.g., BTCUSDT)");
                    String interval = prompt(sc, "Interval (e.g., 1m/5m/15m)");
                    int lookback = Integer.parseInt(prompt(sc, "Lookback (e.g., 200)"));
                    System.out.println("[TODO] start engine: " + symbol + " " + interval + " " + lookback);
                    pause(sc);
                }
                case "2" -> { System.out.println("[TODO] start BTCUSDT 1m 200"); pause(sc); }
                case "3" -> { System.out.println("[TODO] start ETHUSDT 5m 500"); pause(sc); }
                case "4" -> { return; }
                default -> System.out.println("Unknown option: " + c);
            }
        }
    }

    private static void profileMenu(Scanner sc) {
        while (true) {
            System.out.println("\n=== Profiles & Accounts ===");
            System.out.println("1) Switch profile");
            System.out.println("2) Switch account");
            System.out.println("3) Back");

            String c = prompt(sc, "Select");
            switch (c) {
                case "1" -> { System.out.println("[TODO] switch profile"); pause(sc); }
                case "2" -> { System.out.println("[TODO] switch account"); pause(sc); }
                case "3" -> { return; }
                default -> System.out.println("Unknown option: " + c);
            }
        }
    }

    private static void printHeader() {
        System.out.println("\n=== Quantor CLI Menu ===");
        // Тут позже красиво показываем статус:
        // Profile/Account/Mode + Config OK + Secrets ENC + Telegram/ChatGPT
        System.out.println("Profile: <default>   Account: <default>   Mode: TEST");
        System.out.println("Config: ?   Secrets: ?   Telegram: ?   ChatGPT: ?");
        System.out.println();
    }

    private static void printMainMenu() {
        System.out.println("1) Setup");
        System.out.println("2) Secrets");
        System.out.println("3) Configure");
        System.out.println("4) Run bot");
        System.out.println("5) Profiles & Accounts");
        System.out.println("6) Exit");
    }

    private static String prompt(Scanner sc, String label) {
        System.out.print(label + ": ");
        return sc.nextLine().trim();
    }

    private static void pause(Scanner sc) {
        System.out.print("\nPress Enter...");
        sc.nextLine();
    }

    private static String mask(String s) {
        if (s == null || s.isBlank()) return "<empty>";
        if (s.length() <= 4) return "****";
        return s.substring(0, 2) + "***" + s.substring(s.length() - 2);
    }
}
