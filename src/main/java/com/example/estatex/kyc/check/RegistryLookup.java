package com.example.estatex.kyc.check;

import java.time.LocalDate;

/** What a national register says about an ID number. */
public record RegistryLookup(
        /** MATCH / PARTIAL / NO_MATCH / NOT_FOUND / DECEASED / ERROR */
        String status,
        String registryName,
        LocalDate registryDateOfBirth,
        String registrySex,
        String detail
) {
    public static RegistryLookup error(String detail) {
        return new RegistryLookup(IdentityCheck.ERROR, null, null, null, detail);
    }
}
