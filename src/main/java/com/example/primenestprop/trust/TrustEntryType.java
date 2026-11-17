package com.example.primenestprop.trust;

/**
 * Movements on an organisation's trust (client money) account. Client money only ever enters the
 * trust ledger; agency income leaves it only through {@link #TRANSFER_TO_OPERATING}, which must
 * reference the fee or commission it pays, so client money and operating income never mix.
 */
public enum TrustEntryType {
    DEPOSIT_RECEIVED(1),
    RENT_RECEIVED(1),
    OTHER_CLIENT_FUNDS_RECEIVED(1),
    DISBURSEMENT_TO_OWNER(-1),
    REFUND_TO_CLIENT(-1),
    PAYMENT_TO_THIRD_PARTY(-1),
    TRANSFER_TO_OPERATING(-1);

    private final int direction;

    TrustEntryType(int direction) {
        this.direction = direction;
    }

    public int direction() {
        return direction;
    }
}
