package com.example.estatex.transaction;

public enum SaleVerification {
    /** Someone said it sold. Counted as "reported sales", never as verified transactions. */
    REPORTED,
    /** EstateX staff checked conveyancer or Deeds Registry evidence. */
    VERIFIED,
    /** The evidence didn't hold up. Kept for the record; excluded from every sales figure. */
    REJECTED
}
