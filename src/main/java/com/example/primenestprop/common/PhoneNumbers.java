package com.example.primenestprop.common;

/**
 * Normalises phone numbers to E.164 so the same person typed three ways ("0772 123 456",
 * "+263 77 212 3456", "263772123456") matches one contact. Zimbabwean local numbers (leading 0,
 * 9-10 digits) get +263; anything with a country code is kept as-is.
 */
public final class PhoneNumbers {
    private PhoneNumbers() {
    }

    /** E.164 form ("+263772123456"), or null when there aren't enough digits to be a phone number. */
    public static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return null;
        }
        if (trimmed.startsWith("+")) {
            return "+" + digits;
        }
        if (digits.startsWith("00")) {
            return "+" + digits.substring(2);
        }
        if (digits.startsWith("263") && digits.length() >= 11) {
            return "+" + digits;
        }
        if (digits.startsWith("0") && (digits.length() == 10 || digits.length() == 9)) {
            return "+263" + digits.substring(1);
        }
        if (digits.length() == 9 && digits.startsWith("7")) {
            return "+263" + digits; // mobile typed without the leading 0
        }
        return "+" + digits;
    }

    /** Digits for wa.me links ("263772123456"). */
    public static String whatsappDigits(String raw) {
        String e164 = normalise(raw);
        return e164 == null ? null : e164.substring(1);
    }
}
