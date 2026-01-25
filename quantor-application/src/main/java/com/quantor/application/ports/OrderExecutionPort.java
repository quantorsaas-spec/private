package com.quantor.application.ports;

public interface OrderExecutionPort {
    void marketBuy(String symbol, double quantity) throws Exception;
    void marketSell(String symbol, double quantity) throws Exception;
}
