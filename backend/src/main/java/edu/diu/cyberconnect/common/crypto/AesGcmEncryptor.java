package edu.diu.cyberconnect.common.crypto;

import edu.diu.cyberconnect.common.config.CryptoProperties;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption for identity documents. Output layout: 12-byte random IV followed by ciphertext + 16-byte
 * tag. The associated data (for example the document id) binds a ciphertext to its record.
 */
@Component
public class AesGcmEncryptor {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKey key;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    public AesGcmEncryptor(CryptoProperties properties) {
        byte[] raw = Base64.getDecoder().decode(properties.documentKey());
        if (raw.length != 32) {
            throw new IllegalStateException("crypto.document-key must decode to exactly 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(raw, "AES");
        this.keyId = properties.keyId();
    }

    public String keyId() {
        return keyId;
    }

    public byte[] encrypt(byte[] plaintext, byte[] associatedData) {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            if (associatedData != null) {
                cipher.updateAAD(associatedData);
            }
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] out = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, out, IV_LENGTH, ciphertext.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] payload, byte[] associatedData) {
        if (payload == null || payload.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Ciphertext is too short");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_LENGTH));
            if (associatedData != null) {
                cipher.updateAAD(associatedData);
            }
            return cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed or data was tampered with", e);
        }
    }
}
