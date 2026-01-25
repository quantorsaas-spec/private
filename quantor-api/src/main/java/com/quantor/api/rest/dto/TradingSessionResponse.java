package com.quantor.api.rest.dto;

import com.quantor.domain.trading.TradingStatus;

public record TradingSessionResponse(
        String sessionId,
        String userId,
        String exchangeId,
        String accountId,
        String strategyId,
        TradingStatus status,
        String startedAt,
        String stoppedAt,
        String stopCode,
        String stopMessage,
        double pnlRealized,
        double pnlUnrealized
) {}
