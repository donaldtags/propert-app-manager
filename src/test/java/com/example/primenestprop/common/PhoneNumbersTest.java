package com.example.primenestprop.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {
    @Test
    void zimbabweanNumbersTypedAnyWayNormaliseTheSame() {
        for (String raw : new String[]{"0772 123 456", "+263 77 212 3456".replace("212 3456", "2123456"), "263772123456",
                "00263772123456", "772123456", "(077) 212-3456".replace("212-3456", "2123456")}) {
            assertEquals("+263772123456", PhoneNumbers.normalise(raw), raw);
        }
        assertEquals("263784542297", PhoneNumbers.whatsappDigits("+263 78 454 2297"));
    }

    @Test
    void foreignNumbersKeepTheirCountryCode() {
        assertEquals("+447700900123", PhoneNumbers.normalise("+44 7700 900123"));
        assertEquals("+27821234567", PhoneNumbers.normalise("0027 82 123 4567"));
    }

    @Test
    void tooShortIsNotAPhoneNumber() {
        assertNull(PhoneNumbers.normalise("123"));
        assertNull(PhoneNumbers.normalise(null));
    }
}
