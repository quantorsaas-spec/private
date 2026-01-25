package com.quantor.application.config;

/**
 * Known configuration keys for Quantor.
 * Secrets are stored in secrets.properties (optionally encrypted as ENC(...)).
 */
public enum ConfigKey {
    CHATGPT_API_URL("chatGptApiUrl", false, false),
    TELEGRAM_BOT_TOKEN("TELEGRAM_BOT_TOKEN", true, false),
    TELEGRAM_CHAT_ID("TELEGRAM_CHAT_ID", true, false),
    BINANCE_API_KEY("BINANCE_API_KEY", true, false),
    BINANCE_API_SECRET("BINANCE_API_SECRET", true, false),

    // Bybit (v5)
    BYBIT_API_KEY("BYBIT_API_KEY", true, false),
    BYBIT_API_SECRET("BYBIT_API_SECRET", true, false),
    BYBIT_BASE_URL("BYBIT_BASE_URL", false, true),
    BYBIT_RECV_WINDOW("BYBIT_RECV_WINDOW", false, true),

    CHATGPT_API_KEY("CHATGPT_API_KEY", true, false),

    // Example non-secret settings (safe defaults)
    LOG_LEVEL("log.level", false, true);

    private final String key;
    private final boolean secret;
    private final boolean optional;

    ConfigKey(String key, boolean secret, boolean optional) {
        this.key = key;
        this.secret = secret;
        this.optional = optional;
    }

    public String key() { return key; }
    public boolean isSecret() { return secret; }
    public boolean isOptional() { return optional; }
}
