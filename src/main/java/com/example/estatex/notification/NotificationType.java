package com.example.estatex.notification;

public enum NotificationType {
    RENT_REMINDER,
    LEASE_EXPIRY,
    MAINTENANCE_UPDATE,
    SECURITY_ALERT,
    /** Agency listing compliance: mandate expired, listing unpublished, approval needed. */
    LISTING_COMPLIANCE,
    /** Short-stay booking requests, decisions, payments and cancellations. */
    STAY_BOOKING,
    /** Agent licence and company (agency) approval decisions. */
    ACCOUNT_VERIFICATION,
    /** Wallet withdrawals paid or rejected. */
    WALLET
}
