package com.quantor.domain.risk;



import com.quantor.domain.order.TradeAction;
public record RiskDecision(boolean allowed, TradeAction actionToExecute, String reason) {

    public static RiskDecision allow(TradeAction action, String reason) {
        return new RiskDecision(true, action, reason);
    }

    public static RiskDecision deny(String reason) {
        return new RiskDecision(false, TradeAction.HOLD, reason);
    }
}