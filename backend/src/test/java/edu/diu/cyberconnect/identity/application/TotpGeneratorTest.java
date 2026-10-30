package edu.diu.cyberconnect.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TotpGeneratorTest {

    /** RFC 6238 appendix B secret and reference time steps (HMAC-SHA1, truncated to 6 digits). */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238ReferenceVectors() {
        assertThat(TotpGenerator.code(RFC_SECRET, 59 / 30)).isEqualTo("287082");
        assertThat(TotpGenerator.code(RFC_SECRET, 1111111109L / 30)).isEqualTo("081804");
        assertThat(TotpGenerator.code(RFC_SECRET, 1234567890L / 30)).isEqualTo("005924");
        assertThat(TotpGenerator.code(RFC_SECRET, 2000000000L / 30)).isEqualTo("279037");
    }

    @Test
    void base32RoundTrips() {
        byte[] secret = TotpGenerator.newSecret();
        String encoded = TotpGenerator.base32Encode(secret);
        assertThat(encoded).matches("[A-Z2-7]+");
        assertThat(TotpGenerator.base32Decode(encoded)).isEqualTo(secret);
        assertThat(TotpGenerator.base32Decode("JBSWY3DPEHPK3PXP")).isEqualTo("Hello!Þ­¾ï".getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void verifiesWithinWindowOnly() {
        byte[] secret = TotpGenerator.newSecret();
        long now = TotpGenerator.currentStep();
        assertThat(TotpGenerator.matchingStep(secret, TotpGenerator.code(secret, now), 1)).isEqualTo(now);
        assertThat(TotpGenerator.matchingStep(secret, TotpGenerator.code(secret, now - 1), 1)).isEqualTo(now - 1);
        assertThat(TotpGenerator.matchingStep(secret, TotpGenerator.code(secret, now - 3), 1)).isEqualTo(-1);
        assertThat(TotpGenerator.matchingStep(secret, "12345", 1)).isEqualTo(-1);
        assertThat(TotpGenerator.matchingStep(secret, null, 1)).isEqualTo(-1);
    }
}
