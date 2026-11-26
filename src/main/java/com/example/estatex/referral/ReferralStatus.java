package com.example.estatex.referral;

public enum ReferralStatus {
    /** Signed up with a code; the discount applies to their first subscription payment. */
    REGISTERED,
    /** Looks like the referrer referring themselves (same device, ID number or email). No discount, no reward. */
    BLOCKED,
    /** First subscription payment succeeded and the referrer was credited. */
    REWARDED,
    /** First payment succeeded, but the referrer had already reached this month's reward limit. */
    CAPPED,
    /** The reward was taken back (refund or staff decision). */
    REVERSED,
    /** The reward should be taken back, but the referrer's wallet doesn't hold enough. Staff follow up. */
    REVERSAL_PENDING
}
