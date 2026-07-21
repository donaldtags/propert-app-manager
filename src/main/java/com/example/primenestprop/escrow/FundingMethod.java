package com.example.primenestprop.escrow;

public enum FundingMethod {
    BANK_TRANSFER("Bank transfer"),
    ECOCASH("EcoCash"),
    ONEMONEY("OneMoney"),
    INNBUCKS("Innbucks"),
    PAYNOW("Paynow"),
    MPESA("M-Pesa"),
    MTN_MOMO("MTN Mobile Money"),
    AIRTEL_MONEY("Airtel Money"),
    PAYFAST("PayFast"),
    CARD("Debit / credit card");

    private final String displayName;

    FundingMethod(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}