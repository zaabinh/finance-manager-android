package com.fintrack.api.service.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Seals a successor refresh token under a key derived from the raw token it replaced.
 * The database only stores SHA-256(raw), so neither a DB dump nor the server alone can open it:
 * only a client presenting the old raw token can, and only during the grace period.
 */
final class SuccessorTokenCipher {
    private static final byte[] KEY_LABEL = "fintrack/refresh-grace/v1".getBytes(StandardCharsets.UTF_8);
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SuccessorTokenCipher() { }

    static byte[] seal(String presentedRaw, UUID sessionId, String successorRaw) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            byte[] sealed = cipher(Cipher.ENCRYPT_MODE, presentedRaw, sessionId, iv)
                    .doFinal(successorRaw.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot seal successor token", exception);
        }
    }

    /** @throws GeneralSecurityException if the token, session or ciphertext does not match */
    static String open(String presentedRaw, UUID sessionId, byte[] sealed) throws GeneralSecurityException {
        if (sealed == null || sealed.length <= IV_BYTES) throw new GeneralSecurityException("No sealed successor");
        byte[] iv = java.util.Arrays.copyOf(sealed, IV_BYTES);
        byte[] plain = cipher(Cipher.DECRYPT_MODE, presentedRaw, sessionId, iv).doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static Cipher cipher(int mode, String presentedRaw, UUID sessionId, byte[] iv) throws GeneralSecurityException {
        // HMAC with a fixed label keeps this key independent of the stored SHA-256 lookup hash.
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(presentedRaw.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var key = new SecretKeySpec(mac.doFinal(KEY_LABEL), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(sessionId.toString().getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
