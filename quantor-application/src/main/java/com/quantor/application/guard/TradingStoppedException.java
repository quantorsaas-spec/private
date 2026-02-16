package com.quantor.application.guard;

public class TradingStoppedException extends RuntimeException {
    public TradingStoppedException(String reason) {
        super(reason);
    }
}
