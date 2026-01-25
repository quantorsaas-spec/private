package com.quantor.domain.trading;

public enum StopReasonCode {
    USER_REQUEST,
    SUBSCRIPTION_REQUIRED,
    MAX_LOSS,
    DAILY_LOSS,
    API_ERROR,
    RECONNECT_FAIL,
    INCONSISTENT_STATE,
    ENGINE_FAILURE
}
