package com.example.estatex.common;

import com.example.estatex.user.AppUser;
import com.example.estatex.user.UserRepository;
import java.util.Optional;

/**
 * The platform was called PrimeNest before EstateX. System accounts created back then still carry
 * the old e-mail and name in existing databases; adopting them (rather than creating new ones)
 * keeps their history, payments and unique phone numbers intact.
 */
public final class LegacyBrand {
    private LegacyBrand() {}

    /** The account under {@code email}, or the legacy PrimeNest account renamed to it. */
    public static Optional<AppUser> adopt(UserRepository users, String email, String legacyEmail, String fullName) {
        Optional<AppUser> current = users.findByEmailIgnoreCase(email);
        if (current.isPresent()) {
            return current;
        }
        return users.findByEmailIgnoreCase(legacyEmail).map(legacy -> {
            legacy.setEmail(email);
            legacy.setFullName(fullName);
            return users.save(legacy);
        });
    }

    /** Old seeded copy mentioned the platform by its former name. */
    public static String rebrand(String text) {
        return text == null ? null : text.replace("PrimeNest", "EstateX");
    }
}
