package com.quantor.api.billing.lemonsqueezy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Verifies Lemon Squeezy webhook signature:
 * signature = hex(HMAC_SHA256(secret, rawBody)).
 *
 * Lemon Squeezy sends it in the X-Signature header.
 */
@Component
public class LemonSqueezySignature {

    private final String webhookSecret;
    private final Environment env;

    public LemonSqueezySignature(
            @Value("${quantor.lemonsqueezy.webhookSecret:}") String webhookSecret,
            Environment env
    ) {
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.env = env;
    }

    public boolean verifyOrBypass(String rawBody, String headerSignature) {
        // Bypass ONLY in dev/local profiles.
        if (webhookSecret.isEmpty()) {
            return env != null && env.acceptsProfiles(Profiles.of("dev", "local"));
        }

        if (headerSignature == null || headerSignature.isBlank()) return false;

        String computed = hmacSha256Hex(webhookSecret, rawBody == null ? "" : rawBody);
        return constantTimeEquals(computed, headerSignature.trim());
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC SHA-256", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        int diff = x.length ^ y.length;
        for (int i = 0; i < Math.min(x.length, y.length); i++) diff |= x[i] ^ y[i];
        return diff == 0;
    }
}
