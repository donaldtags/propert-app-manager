package com.example.primenestprop.organisation;

/** The business lines an organisation can switch on. ESTATEX Group runs all five. */
public enum Division {
    PROPERTIES,
    MANAGEMENT,
    INTELLIGENCE,
    DEVELOPMENTS,
    INVESTMENTS;

    static class SetConverter extends com.example.primenestprop.common.EnumSetConverter<Division> {
        SetConverter() {
            super(Division.class);
        }
    }
}
