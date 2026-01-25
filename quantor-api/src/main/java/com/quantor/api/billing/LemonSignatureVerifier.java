package com.quantor.api.billing;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Lemon Squeezy webhook signature verifier.
 *
 * Lemon Squeezy sends HMAC SHA-256 hex digest of the raw request body in X-Signature. 
 */
public final class LemonSignatureVerifier {

    private LemonSignatureVerifier() {}

    public static boolean verify(byte[] rawBody, String headerSignature, String secret) {
        if (rawBody == null || headerSignature == null || secret == null) return false;

        String digestHex = hmacSha256Hex(rawBody, secret);
        byte[] a = digestHex.getBytes(StandardCharsets.UTF_8);
        byte[] b = headerSignature.getBytes(StandardCharsets.UTF_8);

        // constant-time compare
        return MessageDigest.isEqual(a, b);
    }

    private static String hmacSha256Hex(byte[] rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal(rawBody);
            return toHex(h);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC calculation failed", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] hexArray = "0123456789abcdef".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
