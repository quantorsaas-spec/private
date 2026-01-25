package com.quantor.cli.tui;

import com.quantor.cli.tools.ConfigDoctor;
import com.quantor.cli.tools.ConfigureTool;
import com.quantor.cli.tools.PreflightTool;
import com.quantor.cli.tools.SecretsEncryptor;
import com.quantor.cli.tools.SetupWizard;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.util.List;
import java.io.IOException;
import java.util.List;

public final class QuantorMenu {

    private QuantorMenu() {}

    public static int run() {
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .jna(true)     // на Windows помогает с клавишами
                .jansi(true)   // цвета/очистка
                .build()) {

            terminal.enterRawMode();

            while (true) {
                int choice = select(terminal,
                        "Quantor CLI Menu (↑ ↓ Enter, Esc = exit)",
                        List.of(
                                "Setup wizard (создать config + секреты)",
                                "Encrypt secrets (secrets.properties -> secrets.enc)",
                                "Validate config (doctor)",
                                "Preflight (Binance/Telegram/ChatGPT checks)",
                                "Configure (enterprise tool)",
                                "Run LIVE engine (BTCUSDT 1m 200)",
                                "Exit"
                        )
                );

                if (choice == -1 || choice == 6) {
                    clear(terminal);
                    println(terminal, "Bye!");
                    return 0;
                }

                clear(terminal);

                int rc;
                switch (choice) {
                    case 0 -> rc = SetupWizard.run(new String[0]);
                    case 1 -> rc = SecretsEncryptor.run();
                    case 2 -> rc = ConfigDoctor.run(new String[]{"--full"});
                    case 3 -> rc = PreflightTool.run(new String[0]);
                    case 4 -> rc = ConfigureTool.run(new String[]{"init"});
                    case 5 -> {
                        // просто подсказка — сам движок у тебя запускается как позиционные args
                        println(terminal, "Запусти: java -jar quantor-cli.jar BTCUSDT 1m 200");
                        rc = 0;
                    }
                    default -> rc = 0;
                }

                println(terminal, "");
                println(terminal, "Result code: " + rc);
                println(terminal, "Press any key to return to menu...");
                terminal.reader().read();
            }

        } catch (Exception e) {
            System.err.println("[QuantorMenu] Error: " + e.getMessage());
            return 20;
        }
    }

    private static int select(Terminal t, String title, List<String> items) throws IOException {
        int idx = 0;
        NonBlockingReader r = t.reader();

        while (true) {
            clear(t);
            println(t, title);
            println(t, "----------------------------------------");

            for (int i = 0; i < items.size(); i++) {
                String prefix = (i == idx) ? "➤ " : "  ";
                println(t, prefix + items.get(i));
            }

            int ch = r.read();

            // Enter
            if (ch == 10 || ch == 13) return idx;

            // ESC / arrows
            if (ch == 27) {
                int ch2 = r.read(150);      // ✅ PowerShell: увеличили таймаут
                if (ch2 == -1) return -1;   // это настоящий Esc

                if (ch2 == 91) {            // '['
                    int ch3 = r.read(150);
                    if (ch3 == 65) {        // 'A' up
                        idx = (idx - 1 + items.size()) % items.size();
                        continue;
                    }
                    if (ch3 == 66) {        // 'B' down
                        idx = (idx + 1) % items.size();
                        continue;
                    }
                    continue;
                }

                // любое другое ESC — выходим
                return -1;
            }

            // fallback: j/k
            if (ch == 'k' || ch == 'K') idx = (idx - 1 + items.size()) % items.size();
            if (ch == 'j' || ch == 'J') idx = (idx + 1) % items.size();
        }
    }

    private static void clear(Terminal t) {
        t.writer().print("\033[H\033[2J");
        t.writer().flush();
    }

    private static void println(Terminal t, String s) {
        t.writer().println(s);
        t.writer().flush();
    }
}
