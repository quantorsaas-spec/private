package com.quantor.infrastructure.notification;

import com.quantor.application.ports.NotifierPort;
/**
 * Safe replacement when Telegram is not configured.
 * Keeps API the same so existing code doesn't change.
 */
public class NullTelegramNotifier extends TelegramNotifier {

    public NullTelegramNotifier() {
        super("", "");
    }

    @Override

    public void send(String text) {
        System.out.println("[TG-OFF] " + text);
    }

    @Override
    public void sendMainMenu(String currentModeLabel) {
        System.out.println("[TG-OFF] sendMainMenu: " + currentModeLabel);
    }

    @Override
    public void removeKeyboard(String text) {
        System.out.println("[TG-OFF] removeKeyboard: " + text);
    }
}