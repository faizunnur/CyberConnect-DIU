package edu.diu.cyberconnect.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.diu.cyberconnect.common.config.CryptoProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmEncryptorTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final AesGcmEncryptor encryptor = new AesGcmEncryptor(new CryptoProperties(KEY, "k-test"));

    @Test
    void roundTripsWithAssociatedData() {
        byte[] plain = "identity document".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "doc-1".getBytes(StandardCharsets.UTF_8);

        byte[] cipher = encryptor.encrypt(plain, aad);

        assertThat(cipher).hasSize(12 + plain.length + 16);
        assertThat(encryptor.decrypt(cipher, aad)).isEqualTo(plain);
        assertThat(encryptor.keyId()).isEqualTo("k-test");
    }

    @Test
    void usesFreshIvPerEncryption() {
        byte[] plain = "same".getBytes(StandardCharsets.UTF_8);
        assertThat(encryptor.encrypt(plain, null)).isNotEqualTo(encryptor.encrypt(plain, null));
    }

    @Test
    void rejectsTamperingAndWrongAssociatedData() {
        byte[] aad = "doc-1".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = encryptor.encrypt("x".getBytes(StandardCharsets.UTF_8), aad);
        cipher[cipher.length - 1] ^= 0x01;

        assertThatThrownBy(() -> encryptor.decrypt(cipher, aad)).isInstanceOf(IllegalStateException.class);

        byte[] fresh = encryptor.encrypt("x".getBytes(StandardCharsets.UTF_8), aad);
        assertThatThrownBy(() -> encryptor.decrypt(fresh, "doc-2".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKeysOfWrongLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new AesGcmEncryptor(new CryptoProperties(shortKey, "k")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
