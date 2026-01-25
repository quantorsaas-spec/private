package com.quantor.cli.tui;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

public class KeyProbe {
    public static void main(String[] args) throws Exception {
        try (Terminal t = TerminalBuilder.builder()
                .system(true)
                .jna(true)
                .jansi(true)
                .build()) {

            t.enterRawMode();
            t.writer().println("KeyProbe: press keys (ESC to exit).");
            t.writer().println("I will print codes. If you press arrows you should see 27,91,65/66.");
            t.writer().flush();

            NonBlockingReader r = t.reader();
            while (true) {
                int ch = r.read();
                t.writer().println("code=" + ch);
                t.writer().flush();
                if (ch == 27) { // ESC
                    int ch2 = r.read(150);
                    t.writer().println(" after-esc=" + ch2);
                    t.writer().flush();
                    if (ch2 == -1) break;
                    int ch3 = r.read(150);
                    t.writer().println(" after-esc-2=" + ch3);
                    t.writer().flush();
                }
            }
        }
    }
}
