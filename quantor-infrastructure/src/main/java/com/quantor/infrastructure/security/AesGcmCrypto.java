package com.quantor.infrastructure.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for secrets.
 *
 * Format: base64( salt(16) || iv(12) || ciphertext+tag )
 */
public final class AesGcmCrypto {

    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITERS = 120_000;

    private static final SecureRandom RNG = new SecureRandom();

    private AesGcmCrypto() {}

    public static String encryptToBase64(String plaintext, char[] password) {
        try {
            byte[] salt = new byte[SALT_LEN];
            RNG.nextBytes(salt);

            byte[] iv = new byte[IV_LEN];
            RNG.nextBytes(iv);

            SecretKey key = deriveKey(password, salt);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));

            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[salt.length + iv.length + ct.length];
            System.arraycopy(salt, 0, out, 0, salt.length);
            System.arraycopy(iv, 0, out, salt.length, iv.length);
            System.arraycopy(ct, 0, out, salt.length + iv.length, ct.length);

            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt secret", e);
        }
    }

    public static String decryptFromBase64(String payloadB64, char[] password) {
        try {
            byte[] payload = Base64.getDecoder().decode(payloadB64);

            if (payload.length < SALT_LEN + IV_LEN + 16) {
                throw new IllegalArgumentException("Encrypted payload too short");
            }

            byte[] salt = new byte[SALT_LEN];
            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[payload.length - SALT_LEN - IV_LEN];

            System.arraycopy(payload, 0, salt, 0, SALT_LEN);
            System.arraycopy(payload, SALT_LEN, iv, 0, IV_LEN);
            System.arraycopy(payload, SALT_LEN + IV_LEN, ct, 0, ct.length);

            SecretKey key = deriveKey(password, salt);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));

            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt secret (wrong password or corrupted data)", e);
        }
    }

    private static SecretKey deriveKey(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERS, KEY_BITS);
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = f.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
