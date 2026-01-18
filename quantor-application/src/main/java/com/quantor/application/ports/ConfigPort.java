package com.quantor.application.ports;

/**
 * Abstraction over configuration and secrets.
 * Infrastructure provides implementation (file/env/vault).
 */
public interface ConfigPort {

    String get(String key);

    String get(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    double getDouble(String key, double defaultValue);

    /** Returns a secret value (may be decrypted by implementation). */
    String getSecret(String key);
}
