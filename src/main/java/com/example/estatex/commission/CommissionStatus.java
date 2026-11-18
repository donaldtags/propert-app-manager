package com.example.estatex.commission;

/**
 * EXPECTED -> EARNED (only via conveyancer confirmation that transfer is registered) -> RECEIVED.
 * EXPECTED may be CANCELLED (deal fell through). There is no other way to change status.
 */
public enum CommissionStatus {
    EXPECTED,
    EARNED,
    RECEIVED,
    CANCELLED
}
