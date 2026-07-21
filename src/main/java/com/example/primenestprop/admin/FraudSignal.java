package com.example.primenestprop.admin;

public record FraudSignal(
        Long propertyId,
        String propertyTitle,
        String type,
        String severity,
        String description
) {
}
