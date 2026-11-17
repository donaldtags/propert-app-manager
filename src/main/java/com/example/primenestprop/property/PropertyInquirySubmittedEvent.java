package com.example.primenestprop.property;

/** A member of the public enquired about an organisation listing from the website. */
public record PropertyInquirySubmittedEvent(Long propertyId, Long organisationId, String name, String email,
                                            String phone, String message) {
}
