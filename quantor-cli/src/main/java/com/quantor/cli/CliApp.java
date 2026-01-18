package com.quantor.cli;


import com.quantor.domain.strategy.Strategy;
import com.quantor.domain.util.Utils;
import com.quantor.infrastructure.db.StrategyRepository;
import com.quantor.infrastructure.db.TradeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

public class CliApp {

    private final Scanner sc = new Scanner(System.in);
    private final StrategyRepository strategyRepo = new StrategyRepository();
    private final TradeRepository tradeRepo = new TradeRepository();
    private final ObjectMapper om = new ObjectMapper();

    public void run() {
        System.out.println("=== BinanceBot CLI (for defense) ===");
        while (true) {
            System.out.println("""
                \nМеню:
                1) Strategies CRUD
                2) Trades history (filter)
                3) Export trades -> CSV
                4) Delete test trades (paper/backtest)
                0) Exit
                """);

            String cmd = ask("Выбор: ");
            try {
                switch (cmd) {
                    case "1" -> strategiesMenu();
                    case "2" -> tradesMenu();
                    case "3" -> exportMenu();
                    case "4" -> deleteTestTrades();
                    case "0" -> { System.out.println("Bye."); return; }
                    default -> System.out.println("Неизвестная команда.");
                }
            } catch (Exception e) {
                System.out.println("❌ Ошибка: " + e.getMessage());
            }
        }
    }

    // ---------- Strategies CRUD ----------
    private void strategiesMenu() throws Exception {
        while (true) {
            System.out.println("""
                \nStrategies CRUD:
                1) Create EMA strategy
                2) List (include disabled)
                3) Search by name
                4) Update params (EMA fast/slow)
                5) Enable/Disable
                6) Delete
                0) Back
                """);
            String cmd = ask("Выбор: ");
            switch (cmd) {
                case "1" -> createEmaStrategy();
                case "2" -> listStrategies();
                case "3" -> searchStrategies();
                case "4" -> updateStrategyParams();
                case "5" -> toggleStrategy();
                case "6" -> deleteStrategy();
                case "0" -> { return; }
                default -> System.out.println("Неизвестно.");
            }
        }
    }

    private void createEmaStrategy() throws Exception {
        String name = ask("Name: ");
        int fast = askIntLoop("EMA fast (>0): ");
        int slow = askIntLoop("EMA slow (>fast): ");

        if (fast <= 0 || slow <= 0 || fast >= slow) {
            System.out.println("❌ Неверные параметры EMA.");
            return;
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "ema");
        params.put("fast", fast);
        params.put("slow", slow);

        String json = om.writeValueAsString(params);

        try {
            long id = strategyRepo.create(name, true, json);
            System.out.println("✅ Created strategy id=" + id);
        } catch (SQLException e) {
            System.out.println("❌ DB error: " + e.getMessage());
        }
    }

    private void listStrategies() throws Exception {
        var list = strategyRepo.listAll(true);
        if (list.isEmpty()) {
            System.out.println("(empty)");
            return;
        }
        for (var s : list) {
            System.out.println(s.id() + " | " + s.name() + " | enabled=" + s.enabled() + " | " + s.paramsJson());
        }
    }

    private void searchStrategies() throws Exception {
        String q = ask("Name contains: ");
        var list = strategyRepo.findByNameLike(q);
        if (list.isEmpty()) {
            System.out.println("(no matches)");
            return;
        }
        for (var s : list) {
            System.out.println(s.id() + " | " + s.name() + " | enabled=" + s.enabled());
        }
    }

    private void updateStrategyParams() throws Exception {
        long id = askLongLoop("Strategy id: ");
        var row = strategyRepo.getById(id);
        if (row == null) { System.out.println("❌ Not found."); return; }

        int fast = askIntLoop("New EMA fast: ");
        int slow = askIntLoop("New EMA slow: ");

        if (fast <= 0 || slow <= 0 || fast >= slow) {
            System.out.println("❌ Неверные параметры EMA.");
            return;
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "ema");
        params.put("fast", fast);
        params.put("slow", slow);

        String json = om.writeValueAsString(params);

        try {
            strategyRepo.updateParams(id, json);
            System.out.println("✅ Updated.");
        } catch (SQLException e) {
            System.out.println("❌ DB error: " + e.getMessage());
        }
    }

    private void toggleStrategy() throws Exception {
        long id = askLongLoop("Strategy id: ");
        var row = strategyRepo.getById(id);
        if (row == null) { System.out.println("❌ Not found."); return; }

        boolean newState = !row.enabled();
        try {
            strategyRepo.setEnabled(id, newState);
            System.out.println("✅ enabled=" + newState);
        } catch (SQLException e) {
            System.out.println("❌ DB error: " + e.getMessage());
        }
    }

    private void deleteStrategy() throws Exception {
        long id = askLongLoop("Strategy id: ");
        try {
            strategyRepo.delete(id);
            System.out.println("✅ Deleted.");
        } catch (SQLException e) {
            System.out.println("❌ DB error: " + e.getMessage());
        }
    }

    // ---------- Trades ----------
    private void tradesMenu() {
        String mode = ask("mode (paper/backtest/live) or empty: ");
        String symbol = ask("symbol (BTCUSDT) or empty: ");
        int limit = askIntLoop("limit (e.g., 20): ");

        try {
            var list = tradeRepo.list(blankToNull(mode), blankToNull(symbol), limit);
            if (list.isEmpty()) {
                System.out.println("(no trades)");
                return;
            }
            for (var t : list) {
                System.out.println(t.id() + " | " + t.ts() + " | " + t.mode() + " | " + t.symbol() + " | " + t.side()
                        + " | price=" + t.price() + " qty=" + t.qty() + " bal=" + t.balanceAfter());
            }
        } catch (SQLException e) {
            System.out.println("❌ DB error: " + e.getMessage());
        }
    }

    private void exportMenu() {
        String mode = ask("mode filter (paper/backtest/live) or empty: ");
        String symbol = ask("symbol filter or empty: ");
        int limit = askIntLoop("limit (e.g., 200): ");

        Path out = Path.of("data", "export_trades.csv");
        try {
            tradeRepo.exportToCsv(blankToNull(mode), blankToNull(symbol), out, limit);
            System.out.println("✅ Exported: " + out.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("❌ Export error: " + e.getMessage());
        }
    }

    private void deleteTestTrades() {
        try {
            int n = tradeRepo.deleteTestTrades();
            System.out.println("✅ Deleted rows: " + n);
        } catch (SQLException e) {
            System.out.println("❌ DB error: " + e.getMessage());
        }
    }

    // ---------- Utils ----------
    private String ask(String prompt) {
        System.out.print(prompt);
        return sc.nextLine().trim();
    }

    private int askIntLoop(String prompt) {
        while (true) {
            String s = ask(prompt);
            try {
                return Integer.parseInt(s);
            } catch (Exception e) {
                System.out.println("❌ Введите число.");
            }
        }
    }

    private long askLongLoop(String prompt) {
        while (true) {
            String s = ask(prompt);
            try {
                return Long.parseLong(s);
            } catch (Exception e) {
                System.out.println("❌ Введите число.");
            }
        }
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isBlank() ? null : s;
    }
}