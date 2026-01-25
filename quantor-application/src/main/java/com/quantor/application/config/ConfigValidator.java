package com.quantor.application.config;

import com.quantor.application.ports.ConfigPort;

public final class ConfigValidator {

    public ConfigValidationResult validate(ConfigPort config) {
        ConfigValidationResult res = new ConfigValidationResult();

        for (ConfigKey k : ConfigKey.values()) {
            if (k.isOptional()) continue;

            String v = k.isSecret() ? config.getSecret(k.key()) : config.get(k.key());
            if (v == null || v.isBlank()) {
                res.addError("Missing required " + (k.isSecret() ? "secret" : "config") + ": " + k.key());
            }
        }

        // Lightweight sanity checks (no network calls)
        String url = config.get(ConfigKey.CHATGPT_API_URL.key(), "");
        if (!url.isBlank() && !(url.startsWith("http://") || url.startsWith("https://"))) {
            res.addError("chatGptApiUrl must start with http:// or https://");
        }

        return res;
    }
}
