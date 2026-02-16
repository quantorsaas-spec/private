package com.quantor.infrastructure.security;

public interface CryptoService {
    /** Encrypts plaintext and returns payload: v1:<b64(iv)>:<b64(ciphertext)> */
    String encryptToPayload(String plaintext);

    /** Decrypts payload v1 and returns plaintext */
    String decryptPayload(String payload);
}
