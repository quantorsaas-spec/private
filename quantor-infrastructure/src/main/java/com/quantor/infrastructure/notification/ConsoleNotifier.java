package com.quantor.infrastructure.notification;

import com.quantor.application.ports.NotifierPort;

public class ConsoleNotifier implements NotifierPort {
    @Override

    public void send(String message) {
        System.out.println("[NOTIFY] " + message);
    }
}