// File: quantor-infrastructure/src/main/java/com/quantor/infrastructure/config/FileConfigService.java
package com.quantor.infrastructure.config;

import com.quantor.application.ports.ConfigPort;
import com.quantor.infrastructure.security.AesGcmCrypto;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Product-grade file + env configuration for Quantor.
 *
 * Load order (low -> high priority):
 *  1) config.properties (profile root)
 *  2) .env (profile root, optional)
 *  3) secrets.properties (profile root, optional)
 *  4) accounts/<account>/.env (optional)
 *  5) accounts/<account>/secrets.properties (optional)
 *  6) OS environment variables (highest priority)
 *
 * Secrets support values like:
 *   ENC(base64(salt||iv||ciphertext+tag))
 *
 * Master password lookup:
 *  - env QUANTOR_MASTER_PASSWORD
 *  - or system property -Dquantor.masterPassword=...
 */
public final class FileConfigService implements ConfigPort {

    private final Properties props = new Properties();
    private final Path profileDir;
    private final Path accountDir;
    private final String masterPassword;
    private final com.quantor.infrastructure.db.UserSecretsStore secretStore =
            new com.quantor.infrastructure.db.UserSecretsStore();

    private FileConfigService(Path profileDir, Path accountDir) throws IOException {
        this.profileDir = profileDir;
        this.accountDir = accountDir;
        this.masterPassword = masterPasswordOrNull();
        loadAll();
        migrateKnownSecretsToDb();
        applyEnvOverrides();
    }

    public static FileConfigService defaultFromWorkingDir() throws IOException {
        return defaultFromWorkingDir(null, null);
    }

    public static FileConfigService defaultFromWorkingDir(String profile) throws IOException {
        return defaultFromWorkingDir(profile, null);
    }

    public static FileConfigService defaultFromWorkingDir(String profile, String account) throws IOException {
        Path base = Path.of(System.getProperty("user.dir")).resolve("config");
        if (profile != null && !profile.isBlank()) {
            base = base.resolve(profile.trim());
        }
        Files.createDirectories(base);

        Path accDir = null;
        if (account != null && !account.isBlank()) {
            accDir = base.resolve("accounts").resolve(account.trim());
            Files.createDirectories(accDir);
        }
        return new FileConfigService(base, accDir);
    }

    public Path getProfileDir() {
        return profileDir;
    }

    public Path getAccountDir() {
        return accountDir;
    }

    private void loadAll() throws IOException {
        if (profileDir == null) return;

        loadPropsIfExists(profileDir.resolve("config.properties"));

        Map<String, String> env = DotEnv.loadIfExists(profileDir.resolve(".env"));
        for (Map.Entry<String, String> e : env.entrySet()) {
            props.setProperty(e.getKey(), e.getValue());
        }

        loadPropsIfExists(profileDir.resolve("secrets.properties"));
        loadPropsIfExists(profileDir.resolve("secrets.enc"));

        if (accountDir != null) {
            Map<String, String> aenv = DotEnv.loadIfExists(accountDir.resolve(".env"));
            for (Map.Entry<String, String> e : aenv.entrySet()) {
                props.setProperty(e.getKey(), e.getValue());
            }
            loadPropsIfExists(accountDir.resolve("secrets.properties"));
            loadPropsIfExists(accountDir.resolve("secrets.enc"));
        }
    }

    private void loadPropsIfExists(Path file) throws IOException {
        if (file == null || !Files.exists(file)) return;
        try (FileInputStream in = new FileInputStream(file.toFile())) {
            props.load(in);
        }
    }

    private void applyEnvOverrides() {
        Map<String, String> env = System.getenv();

        // override known keys via QUANTOR_* mapping
        for (String key : new LinkedHashMap<>(propsToMap()).keySet()) {
            String envKey = toEnvKey(key);
            String val = env.get(envKey);
            if (val != null) props.setProperty(key, val);
        }

        // also support direct env overrides: BINANCE_API_KEY etc.
        for (Map.Entry<String, String> e : env.entrySet()) {
            if (props.containsKey(e.getKey())) {
                props.setProperty(e.getKey(), e.getValue());
            }
        }
    }

    private Map<String, String> propsToMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : props.stringPropertyNames()) {
            map.put(name, props.getProperty(name));
        }
        return map;
    }

    /**
     * Maps a Java-properties key into an env-var key.
     *
     * Examples:
     * - telegram.botToken  -> QUANTOR_TELEGRAM_BOT_TOKEN
     * - telegram.chatId    -> QUANTOR_TELEGRAM_CHAT_ID
     * - apiKey             -> QUANTOR_API_KEY
     */
    private static String toEnvKey(String key) {
        String s = key.replace('.', '_');
        s = s.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        return "QUANTOR_" + s.toUpperCase();
    }

    private static String masterPasswordOrNull() {
        String env = System.getenv("QUANTOR_MASTER_PASSWORD");
        if (env != null && !env.isBlank()) return env;
        String sys = System.getProperty("quantor.masterPassword");
        if (sys != null && !sys.isBlank()) return sys;
        return null;
    }

    @Override
    public String get(String key) {
        return get(key, null);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String v = get(key, null);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        String v = get(key, null);
        if (v == null) return defaultValue;
        try {
            return Double.parseDouble(v.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public String get(String key, String defaultValue) {
        String v = props.getProperty(key);
        return (v == null) ? defaultValue : v;
    }

    @Override
    public String getSecret(String key) {
        return getSecret(key, null);
    }

    public String getSecret(String key, String defaultValue) {
        // 1) DB secrets (preferred)
        String userId = resolveUserId();
        if (masterPassword != null && !masterPassword.isBlank()) {
            try {
                String fromDb = secretStore.getDecrypted(userId, key, masterPassword.toCharArray());
                if (fromDb != null && !fromDb.isBlank()) return fromDb;
            } catch (Exception ignore) {
                // IMPORTANT: do not crash app if DB contains stale/corrupted ciphertext
                // (e.g., master password rotated). We'll fall back to file/env.
            }
        }

        // 2) File secrets (fallback)
        String v = props.getProperty(key);
        if (v == null) return defaultValue;

        String trimmed = v.trim();
        if (trimmed.startsWith("ENC(") && trimmed.endsWith(")")) {
            if (masterPassword == null || masterPassword.isBlank()) {
                throw new IllegalStateException("Secret is encrypted but QUANTOR_MASTER_PASSWORD is not set: " + key);
            }
            String payload = trimmed.substring(4, trimmed.length() - 1);
            return AesGcmCrypto.decryptFromBase64(payload, masterPassword.toCharArray());
        }
        return v;
    }

    private String resolveUserId() {
        String uid = props.getProperty("userId");
        if (uid == null || uid.isBlank()) uid = props.getProperty("account");
        if (uid == null || uid.isBlank()) uid = "local";
        return uid.trim();
    }

    /**
     * One-time migration: if secrets exist in files, copy them into DB encrypted.
     * DB is always preferred afterwards.
     *
     * Safety:
     * - If DB already has value => keep it (do not overwrite).
     * - If file secret is broken ENC(...) => skip (do not crash app).
     */
    private void migrateKnownSecretsToDb() {
        if (masterPassword == null || masterPassword.isBlank()) return;

        String migrateFlag = props.getProperty("secrets.migrateToDb", "true");
        if (!Boolean.parseBoolean(migrateFlag)) return;

        String userId = resolveUserId();
        java.util.List<String> keys = new java.util.ArrayList<>();

        for (com.quantor.application.config.ConfigKey ck : com.quantor.application.config.ConfigKey.values()) {
            if (ck.isSecret()) keys.add(ck.key());
        }

        // Legacy aliases kept for backward compatibility
        keys.add("apiKey");
        keys.add("apiSecret");
        keys.add("telegram.botToken");
        keys.add("telegram.chatId");

        for (String k : keys) {
            String current = props.getProperty(k);
            if (current == null || current.isBlank()) continue;

            // already in db?
            String inDb = secretStore.getEncrypted(userId, k);
            if (inDb != null && !inDb.isBlank()) continue;

            String plaintext = current.trim();

            // decrypt if value is ENC(...)
            if (plaintext.startsWith("ENC(") && plaintext.endsWith(")")) {
                try {
                    String payload = plaintext.substring(4, plaintext.length() - 1);
                    plaintext = AesGcmCrypto.decryptFromBase64(payload, masterPassword.toCharArray());
                } catch (Exception e) {
                    // skip broken secret; don't brick startup
                    continue;
                }
            }

            try {
                secretStore.putPlaintext(userId, k, plaintext, masterPassword.toCharArray());
            } catch (Exception ignore) {
                // no-op: migration must not break startup
            }
        }
    }
}
