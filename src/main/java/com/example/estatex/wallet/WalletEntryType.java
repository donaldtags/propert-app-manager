package com.example.estatex.wallet;

/** Entry types, string-coded in the database so new ones never need a CHECK-constraint migration. */
public final class WalletEntryType {
    public static final String TOP_UP = "TOP_UP";
    public static final String PAYMENT = "PAYMENT";
    public static final String PAYOUT = "PAYOUT";
    public static final String WITHDRAWAL = "WITHDRAWAL";
    public static final String WITHDRAWAL_REVERSAL = "WITHDRAWAL_REVERSAL";
    /** System-only: a held withdrawal leaves EstateX once staff have paid it out. */
    public static final String WITHDRAWAL_PAID = "WITHDRAWAL_PAID";
    public static final String FEE = "FEE";
    public static final String ADJUSTMENT = "ADJUSTMENT";
    /** Credit to a referrer when a friend they invited first pays for a subscription. */
    public static final String REFERRAL_REWARD = "REFERRAL_REWARD";
    /** A referral reward taken back (refund or staff decision). */
    public static final String REFERRAL_REVERSAL = "REFERRAL_REVERSAL";

    private WalletEntryType() {}
}
