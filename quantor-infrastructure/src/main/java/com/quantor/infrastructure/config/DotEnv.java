package com.quantor.infrastructure.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal .env loader:
 * KEY=value
 * Lines starting with # are comments.
 * Empty lines are ignored.
 */
public final class DotEnv {

    private DotEnv() {}

    public static Map<String, String> loadIfExists(Path envFile) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        if (envFile == null || !Files.exists(envFile)) return map;

        for (String line : Files.readAllLines(envFile)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            int eq = t.indexOf('=');
            if (eq <= 0) continue;

            String key = t.substring(0, eq).trim();
            String val = t.substring(eq + 1).trim();

            // remove optional surrounding quotes
            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                val = val.substring(1, val.length() - 1);
            }

            map.put(key, val);
        }
        return map;
    }
}
