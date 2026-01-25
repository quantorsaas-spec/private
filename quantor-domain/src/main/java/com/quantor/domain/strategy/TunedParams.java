package com.quantor.domain.strategy;

/**
 * A set of strategy parameters that we want to control / tune.
 */
public record TunedParams(
        int fastEma,
        int slowEma,
        double stopLossPct,
        double takeProfitPct,
        double positionUSDT
) {
}