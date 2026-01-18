package com.quantor.infrastructure.db;

import com.quantor.infrastructure.security.AesGcmCrypto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

/**
 * Stores per-user secrets in SQLite.
 * Values are encrypted (AES-GCM) using the master password.
 */
public class UserSecretsStore {

    static {
        Database.initSchema();
    }

    public String getDecrypted(String userId, String key, char[] masterPassword) {
        String enc = getEncrypted(userId, key);
        if (enc == null) return null;
        return AesGcmCrypto.decryptFromBase64(enc, masterPassword);
    }

    public String getEncrypted(String userId, String key) {
        String sql = "SELECT secret_value_enc FROM user_secrets WHERE user_id=? AND secret_key=?";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (Exception e) {
            System.err.println("UserSecretsStore.getEncrypted DB error: " + e.getMessage());
        }
        return null;
    }

    public void putEncrypted(String userId, String key, String encryptedPayloadB64) {
        String sql = "INSERT INTO user_secrets(user_id, secret_key, secret_value_enc, updated_at) VALUES(?,?,?,?) " +
                "ON CONFLICT(user_id, secret_key) DO UPDATE SET secret_value_enc=excluded.secret_value_enc, updated_at=excluded.updated_at";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, key);
            ps.setString(3, encryptedPayloadB64);
            ps.setString(4, new Timestamp(System.currentTimeMillis()).toString());
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("UserSecretsStore.putEncrypted DB error: " + e.getMessage());
        }
    }

    public void putPlaintext(String userId, String key, String plaintext, char[] masterPassword) {
        String enc = AesGcmCrypto.encryptToBase64(plaintext, masterPassword);
        putEncrypted(userId, key, enc);
    }
}
