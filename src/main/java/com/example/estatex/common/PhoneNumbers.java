package com.example.estatex.common;

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

    /**
     * Strict form for account phone numbers (sign-in, SMS codes): the normalised E.164 number, or
     * empty if the text isn't a plausible phone number (8-15 digits, no letters or "@").
     */
    public static java.util.Optional<String> forAccount(String raw) {
        if (raw == null || raw.isBlank() || !raw.trim().matches("^[+\\d\\s().-]+$")) {
            return java.util.Optional.empty();
        }
        String e164 = normalise(raw);
        return e164 != null && e164.matches("^\\+[1-9]\\d{7,14}$") ? java.util.Optional.of(e164) : java.util.Optional.empty();
    }

    /** "+263••••••4567": enough for someone to recognise their own number, not to learn it. */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 8) {
            return null;
        }
        String start = e164.startsWith("+263") ? "+263" : e164.substring(0, 3);
        return start + "•".repeat(Math.max(2, e164.length() - start.length() - 4)) + e164.substring(e164.length() - 4);
    }

    /** Digits for wa.me links ("263772123456"). */
    public static String whatsappDigits(String raw) {
        String e164 = normalise(raw);
        return e164 == null ? null : e164.substring(1);
    }
}
