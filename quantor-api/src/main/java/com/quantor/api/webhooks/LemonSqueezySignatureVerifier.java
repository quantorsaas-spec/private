package com.quantor.api.webhooks;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class LemonSqueezySignatureVerifier {

    private LemonSqueezySignatureVerifier() {}

    public static void verifyOrThrow(String signingSecret, String headerSignatureHex, byte[] rawBody) {
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalStateException("Lemon Squeezy webhook secret is not configured");
        }
        if (headerSignatureHex == null || headerSignatureHex.isBlank()) {
            throw new SecurityException("Missing X-Signature header");
        }
        if (rawBody == null || rawBody.length == 0) {
            throw new SecurityException("Empty webhook body");
        }

        String computedHex = hmacSha256Hex(signingSecret, rawBody);

        // constant-time compare
        byte[] a = computedHex.getBytes(StandardCharsets.UTF_8);
        byte[] b = headerSignatureHex.trim().getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(a, b)) {
            throw new SecurityException("Invalid webhook signature");
        }
    }

    private static String hmacSha256Hex(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data);
            return toHexLower(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    private static String toHexLower(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = alphabet[v >>> 4];
            hex[i * 2 + 1] = alphabet[v & 0x0F];
        }
        return new String(hex);
    }
}
