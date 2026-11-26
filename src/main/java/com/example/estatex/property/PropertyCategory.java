package com.example.estatex.property;

/**
 * What kind of property a listing is. Used to break market figures down by property type.
 * Listings created before 2026-11 have no category; analytics counts those as "Unspecified"
 * and never guesses one.
 */
public enum PropertyCategory {
    HOUSE,
    FLAT,
    TOWNHOUSE,
    COTTAGE,
    ROOM,
    STAND,
    COMMERCIAL,
    INDUSTRIAL,
    FARM,
    OTHER
}
