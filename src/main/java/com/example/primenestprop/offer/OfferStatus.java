package com.example.primenestprop.offer;

public enum OfferStatus {
    RECEIVED,
    PRESENTED,
    COUNTERED,
    ACCEPTED,
    REJECTED,
    WITHDRAWN;

    public boolean isOpen() {
        return this == RECEIVED || this == PRESENTED || this == COUNTERED;
    }
}
