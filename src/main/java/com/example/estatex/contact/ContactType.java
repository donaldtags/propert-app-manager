package com.example.estatex.contact;

public enum ContactType {
    OWNER,
    BUYER,
    TENANT,
    LANDOWNER,
    INVESTOR,
    REFERRAL_PARTNER;

    static class SetConverter extends com.example.estatex.common.EnumSetConverter<ContactType> {
        SetConverter() {
            super(ContactType.class);
        }
    }
}
