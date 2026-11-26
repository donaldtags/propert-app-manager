package com.example.estatex.transaction;

/** Where the information about a sale came from. Only the last two can verify a sale. */
public enum SaleSource {
    OWNER_REPORTED,
    AGENT_REPORTED,
    /** A conveyancer's written confirmation that transfer was registered. */
    CONVEYANCER_CONFIRMED,
    /** A Deeds Registry record (deed of transfer number). */
    DEEDS_REGISTRY;

    public boolean canVerify() {
        return this == CONVEYANCER_CONFIRMED || this == DEEDS_REGISTRY;
    }
}
