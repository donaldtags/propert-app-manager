package com.example.estatex.kyc.check;

import com.example.estatex.kyc.IdDocumentType;
import java.time.LocalDate;

/** Looks an ID number up in a national register (e.g. Dojah for Zimbabwe). */
public interface RegistryProvider {
    String name();

    /** Configured (keys present) and able to check this country's ID type. */
    boolean supports(String countryIso2, IdDocumentType type);

    RegistryLookup lookup(String countryIso2, IdDocumentType type, String idNumber, String legalFullName, LocalDate dateOfBirth);
}
