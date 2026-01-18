package com.quantor.cli.tools;

import com.quantor.infrastructure.security.AesGcmCrypto;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Encrypts config/secrets.properties into config/secrets.enc using AES-GCM.
 * Master password lookup:
 *  - env QUANTOR_MASTER_PASSWORD
 *  - or system property -Dquantor.masterPassword=...
 *  - otherwise asks in console
 */
public final class SecretsEncryptor {

    private SecretsEncryptor() {}

    public static int run() {
        try {
            Path root = Paths.get("").toAbsolutePath();
            Path configDir = root.resolve("config");
            Path plain = configDir.resolve("secrets.properties");
            Path example = configDir.resolve("secrets.properties.example");
            Path out = configDir.resolve("secrets.enc");

            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            if (!Files.exists(plain)) {
                if (Files.exists(example)) {
                    Files.copy(example, plain);
                    System.out.println("[Quantor] Created " + plain + " from example. Fill it and re-run.");
                } else {
                    Files.writeString(plain, "# Fill and re-run\n");
                    System.out.println("[Quantor] Created empty " + plain + ". Fill it and re-run.");
                }
                return 2;
            }

            // ✅ password from ENV / system property first
            char[] password = masterPasswordFromEnvOrSys();

            // fallback to prompt
            if (password == null || password.length == 0) {
                Console console = System.console();
                if (console != null) {
                    password = console.readPassword("Enter master password to encrypt secrets: ");
                } else {
                    // IDE/CI fallback (no secure input available)
                    System.out.print("Enter master password to encrypt secrets: ");
                    password = new String(System.in.readAllBytes()).trim().toCharArray();
                }
            }

            if (password == null || password.length == 0) {
                System.err.println("[Quantor] Empty password. Aborting.");
                return 3;
            }

            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(plain)) {
                p.load(in);
            }

            Properties enc = new Properties();

            for (String name : p.stringPropertyNames()) {
                String value = p.getProperty(name, "");
                if (value == null) value = "";
                value = value.trim();

                if (value.isEmpty()) {
                    enc.setProperty(name, "");
                } else if (value.startsWith("ENC(") && value.endsWith(")")) {
                    enc.setProperty(name, value);
                } else {
                    String b64 = AesGcmCrypto.encryptToBase64(value, password);
                    enc.setProperty(name, "ENC(" + b64 + ")");
                }
            }

            // wipe password in memory
            for (int i = 0; i < password.length; i++) password[i] = 0;

            try (OutputStream os = Files.newOutputStream(out)) {
                enc.store(os, "Quantor encrypted secrets (AES-GCM). Values are ENC(base64)");
            }

            System.out.println("[Quantor] Encrypted secrets written to: " + out);
            System.out.println("[Quantor] You can now delete plaintext file: " + plain);
            return 0;

        } catch (IOException e) {
            System.err.println("[Quantor] IO error: " + e.getMessage());
            return 10;
        } catch (Exception e) {
            System.err.println("[Quantor] Error: " + e.getMessage());
            return 11;
        }
    }

    private static char[] masterPasswordFromEnvOrSys() {
        String env = System.getenv("QUANTOR_MASTER_PASSWORD");
        if (env != null && !env.isBlank()) return env.toCharArray();

        String sys = System.getProperty("quantor.masterPassword");
        if (sys != null && !sys.isBlank()) return sys.toCharArray();

        return null;
    }
}
