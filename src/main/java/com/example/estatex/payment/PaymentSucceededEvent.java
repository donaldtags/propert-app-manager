package com.example.estatex.payment;

/** Published once a {@link Payment} flips to {@link PaymentStatus#SUCCESSFUL}, whether via
 * manual self-report or a verified gateway checkout. Domain services (escrow, subscription,
 * featured listings, investments) listen for this instead of {@link PaymentService} calling
 * each of them directly, so none of them have to depend on each other. */
public record PaymentSucceededEvent(Payment payment) {
}
