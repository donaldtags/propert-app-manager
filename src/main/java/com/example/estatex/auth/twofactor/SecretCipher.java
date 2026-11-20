package com.example.estatex.auth.twofactor;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts authenticator secrets before they reach the database (AES-256-GCM), so a leaked
 * database dump alone can't generate anyone's sign-in codes. The key comes from
 * APP_2FA_ENCRYPTION_KEY (any long random string); without it a fixed development key is used
 * and a warning is logged at startup.
 */
@Component
public class SecretCipher {
    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);
    private static final String DEV_KEY = "estatex-development-only-2fa-key-change-me";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.auth.two-factor.encryption-key:}") String configuredKey) {
        String material = configuredKey == null || configuredKey.isBlank() ? DEV_KEY : configuredKey;
        if (material.equals(DEV_KEY)) {
            log.warn("APP_2FA_ENCRYPTION_KEY is not set: two-step sign-in secrets use a development key. "
                    + "Set it to a long random value in production (and never change it afterwards).");
        }
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt secret", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt a two-step sign-in secret (was the key changed?)", e);
        }
    }
}
