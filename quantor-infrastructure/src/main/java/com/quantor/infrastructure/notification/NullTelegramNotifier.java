// File: quantor-infrastructure/src/main/java/com/quantor/infrastructure/notification/NullTelegramNotifier.java
package com.quantor.infrastructure.notification;

/**
 * Safe replacement when Telegram is not configured.
 * Keeps API the same so existing code doesn't change.
 *
 * IMPORTANT:
 * - We intentionally DO NOT use @Override on sendMainMenu/removeKeyboard,
 *   because TelegramNotifier may differ between branches/modules and not declare them.
 * - This class must always compile.
 */
public class NullTelegramNotifier extends TelegramNotifier {

    public NullTelegramNotifier() {
        super("", "");
    }

    @Override
    public void send(String text) {
        System.out.println("[TG-OFF] " + text);
    }

    // Not annotated with @Override intentionally (see comment above)
    public void sendMainMenu(String currentModeLabel) {
        System.out.println("[TG-OFF] sendMainMenu: " + currentModeLabel);
    }

    // Not annotated with @Override intentionally (see comment above)
    public void removeKeyboard(String text) {
        System.out.println("[TG-OFF] removeKeyboard: " + text);
    }
}
