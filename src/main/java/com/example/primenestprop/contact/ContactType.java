package com.example.primenestprop.contact;

public enum ContactType {
    OWNER,
    BUYER,
    TENANT,
    LANDOWNER,
    INVESTOR,
    REFERRAL_PARTNER;

    static class SetConverter extends com.example.primenestprop.common.EnumSetConverter<ContactType> {
        SetConverter() {
            super(ContactType.class);
        }
    }
}
