package com.example.estatex.organisation;

/** The business lines an organisation can switch on. ESTATEX Group runs all five. */
public enum Division {
    PROPERTIES,
    MANAGEMENT,
    INTELLIGENCE,
    DEVELOPMENTS,
    INVESTMENTS;

    static class SetConverter extends com.example.estatex.common.EnumSetConverter<Division> {
        SetConverter() {
            super(Division.class);
        }
    }
}
