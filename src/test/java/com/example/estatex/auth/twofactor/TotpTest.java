package com.example.estatex.auth.twofactor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TotpTest {
    /** RFC 6238 appendix B, SHA1 secret "12345678901234567890" (last 6 of the 8-digit values). */
    private static final String RFC_SECRET = Totp.base32Encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesTheRfc6238TestVectors() {
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", RFC_SECRET);
        assertEquals("287082", Totp.codeAt(RFC_SECRET, Totp.stepAt(59)));
        assertEquals("081804", Totp.codeAt(RFC_SECRET, Totp.stepAt(1111111109)));
        assertEquals("050471", Totp.codeAt(RFC_SECRET, Totp.stepAt(1111111111)));
        assertEquals("005924", Totp.codeAt(RFC_SECRET, Totp.stepAt(1234567890)));
        assertEquals("279037", Totp.codeAt(RFC_SECRET, Totp.stepAt(2000000000)));
    }

    @Test
    void acceptsOneStepEitherSideForClockDriftButNoMore() {
        long now = 1_234_567_890;
        long step = Totp.stepAt(now);
        assertEquals(step, Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, step), now).getAsLong());
        assertTrue(Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, step - 1), now).isPresent());
        assertTrue(Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, step + 1), now).isPresent());
        assertTrue(Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, step + 3), now).isEmpty());
        assertTrue(Totp.matchingStep(RFC_SECRET, "12a456", now).isEmpty());
        assertTrue(Totp.matchingStep(RFC_SECRET, null, now).isEmpty());
    }

    @Test
    void base32RoundTrips() {
        byte[] data = {0, 1, 2, (byte) 250, (byte) 255, 42, 7, 8, 9, 10};
        assertArrayEquals(data, Totp.base32Decode(Totp.base32Encode(data)));
        assertEquals(32, Totp.newSecret().length(), "160-bit secrets");
    }

    @Test
    void otpauthLinkCarriesIssuerAndSecret() {
        String uri = Totp.otpauthUri("EstateX", "tariro@example.com", "ABC");
        assertTrue(uri.startsWith("otpauth://totp/EstateX%3Atariro%40example.com?secret=ABC&issuer=EstateX"), uri);
    }

    @Test
    void secretsAreEncryptedAtRest() {
        SecretCipher cipher = new SecretCipher("test-key");
        String sealed = cipher.encrypt("JBSWY3DPEHPK3PXP");
        assertTrue(!sealed.contains("JBSWY3DPEHPK3PXP"));
        assertEquals("JBSWY3DPEHPK3PXP", cipher.decrypt(sealed));
        assertTrue(!sealed.equals(cipher.encrypt("JBSWY3DPEHPK3PXP")), "random IV each time");
    }
}
